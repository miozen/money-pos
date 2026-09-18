package com.money.feature.gms.application.product;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.*;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.mapper.SysBrandConfigMapper;
import com.money.feature.gms.application.catalog.GmsBrandService;
import com.money.feature.gms.application.catalog.GmsGoodsCategoryService;
import com.money.service.SysDictDetailService;
import com.money.utils.PinyinUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmsGoodsExcelManager {

    private final GmsGoodsService gmsGoodsService;
    private final GmsGoodsCategoryService gmsGoodsCategoryService;
    private final GmsBrandService gmsBrandService;
    private final SysDictDetailService sysDictDetailService;
    private final SysBrandConfigMapper sysBrandConfigMapper;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;

    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public String importGoods(MultipartFile file) {
        log.info("开始执行动态智能 Excel 商品导入引擎(智能脱壳 + 自动建档版)...");

        List<GmsGoodsCategory> categoryList = gmsGoodsCategoryService.list();
        Map<String, Long> catName2IdMap = categoryList.stream().collect(Collectors.toMap(GmsGoodsCategory::getName, GmsGoodsCategory::getId, (k1, k2) -> k1));

        List<GmsBrand> brandList = gmsBrandService.list();
        Map<String, Long> brandName2IdMap = brandList.stream().collect(Collectors.toMap(GmsBrand::getName, GmsBrand::getId, (k1, k2) -> k1));

        Map<String, String> dictReverseMap = sysDictDetailService.getValueToCnDescMap("memberType").entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (k1, k2) -> k1));

        List<SysBrandConfig> brandConfigs = sysBrandConfigMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Boolean> brandDualTrackRadar = new HashMap<>();
        if (brandConfigs != null) {
            for (SysBrandConfig config : brandConfigs) {
                if (config.getCouponEnabled() != null) brandDualTrackRadar.put(config.getBrand(), config.getCouponEnabled());
            }
        }

        List<GmsGoods> parsedGoodsList = new ArrayList<>();
        Map<String, Map<String, BigDecimal>> skuLevelPriceMap = new HashMap<>();
        Map<String, Map<String, BigDecimal>> skuLevelCouponMap = new HashMap<>();

        Map<String, Integer> headerIndexMap = new HashMap<>();
        Map<Integer, String> dynamicPriceColMap = new HashMap<>();
        int[] skipCount = new int[]{0};

        EasyExcel.read(file.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
                    String headName = entry.getValue();
                    if (StrUtil.isBlank(headName)) continue;
                    headName = headName.trim();

                    // ==========================================
                    // 🌟 核心引擎 1：表头脱壳滤网！
                    // 把 "*商品条码(必填且唯一)" 洗成纯净的 "商品条码"
                    // ==========================================
                    String cleanName = headName.replaceAll("[\\*]", "")
                            .replaceAll("\\(.*?\\)", "")
                            .replaceAll("（.*?）", "")
                            .trim();

                    headerIndexMap.put(cleanName, entry.getKey());
                    headerIndexMap.put(headName, entry.getKey());

                    // 会员价列特殊处理
                    if (headName.startsWith("[会员特价] ")) {
                        String levelCode = dictReverseMap.get(headName.replace("[会员特价] ", "").trim());
                        if (StrUtil.isNotBlank(levelCode)) dynamicPriceColMap.put(entry.getKey(), levelCode);
                    }
                }
            }

            // 智能取值器
            private String getVal(Map<Integer, String> data, String... possibleNames) {
                for (String name : possibleNames) {
                    Integer idx = headerIndexMap.get(name);
                    if (idx != null && StrUtil.isNotBlank(data.get(idx))) {
                        return data.get(idx).trim();
                    }
                }
                return null;
            }

            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                String barcode = getVal(data, "商品条码", "条码");
                String name = getVal(data, "商品名称", "名称");

                if (StrUtil.isBlank(barcode) || StrUtil.isBlank(name)) {
                    skipCount[0]++;
                    return;
                }

                GmsGoods goods = new GmsGoods();
                goods.setBarcode(barcode);
                goods.setName(name);
                goods.setMnemonicCode(PinyinUtil.getFirstLetter(goods.getName()));
                goods.setIsCombo(0);

                // ==========================================
                // 🌟 核心引擎 2：智能自动创建新分类
                // ==========================================
                String catCn = getVal(data, "商品分类", "所属分类");
                if (StrUtil.isNotBlank(catCn)) {
                    Long catId = catName2IdMap.get(catCn);
                    // 字典里没找到？说明是新分类，当场创建！
                    if (catId == null) {
                        GmsGoodsCategory newCat = new GmsGoodsCategory();
                        newCat.setName(catCn);
                        // 🌟 核心修复：指定为一级分类（根节点），满足数据库校验！
                        newCat.setPid(0L);

                        gmsGoodsCategoryService.save(newCat);
                        catId = newCat.getId();
                        catName2IdMap.put(catCn, catId);
                    }
                    goods.setCategoryId(catId);
                }

                // ==========================================
                // 🌟 核心引擎 3：智能自动创建新品牌 (已去除 setSort)
                // ==========================================
                String brandCn = getVal(data, "品牌归属", "所属品牌", "商品品牌");
                if (StrUtil.isNotBlank(brandCn)) {
                    Long brandId = brandName2IdMap.get(brandCn);
                    // 字典里没找到？当场创建新品牌！
                    if (brandId == null) {
                        GmsBrand newBrand = new GmsBrand();
                        newBrand.setName(brandCn);
                        gmsBrandService.save(newBrand);
                        brandId = newBrand.getId();
                        // 同步更新字典
                        brandName2IdMap.put(brandCn, brandId);
                    }
                    goods.setBrandId(brandId);
                }

                String discountStr = getVal(data, "参与满减", "是否满减");
                if ("允许".equals(discountStr) || "是".equals(discountStr) || "1".equals(discountStr)) {
                    goods.setIsDiscountParticipable(1);
                } else {
                    goods.setIsDiscountParticipable(0);
                }

                String statusStr = getVal(data, "状态", "上架状态", "商品状态");
                goods.setStatus((statusStr != null && statusStr.contains("SOLD_OUT")) ? "SOLD_OUT" : "SALE");

                goods.setUnit(getVal(data, "单位", "计量单位"));
                goods.setSize(getVal(data, "规格", "规格尺寸"));

                String salePriceStr = getVal(data, "零售价", "系统零售价", "建议零售价");
                BigDecimal salePrice = new BigDecimal(salePriceStr != null ? salePriceStr : "0");
                goods.setSalePrice(salePrice);

                String costPriceStr = getVal(data, "进货价", "进货成本", "加权平均成本价");
                goods.setAvgCostPrice(new BigDecimal(costPriceStr != null ? costPriceStr : "0"));
                goods.setPurchasePrice(goods.getAvgCostPrice());

                String stockStr = getVal(data, "当前库存", "库存", "初始库存");
                goods.setStock(stockStr != null ? Long.parseLong(stockStr) : 0L);

                parsedGoodsList.add(goods);

                boolean isDualTrackBrand = false;
                if (goods.getBrandId() != null) {
                    isDualTrackBrand = brandDualTrackRadar.getOrDefault(String.valueOf(goods.getBrandId()), false);
                }

                Map<String, BigDecimal> currentPrices = new HashMap<>();
                for (Map.Entry<Integer, String> entry : dynamicPriceColMap.entrySet()) {
                    String priceStr = data.get(entry.getKey());
                    if (StrUtil.isNotBlank(priceStr)) {
                        try { currentPrices.put(entry.getValue(), new BigDecimal(priceStr.trim())); } catch (Exception ignored) {}
                    }
                }

                String vipP = getVal(data, "普通会员价"); if (vipP != null) currentPrices.put("VIP", new BigDecimal(vipP));
                String goldP = getVal(data, "黄金会员价"); if (goldP != null) currentPrices.put("GOLD", new BigDecimal(goldP));
                String platP = getVal(data, "铂金会员价"); if (platP != null) currentPrices.put("PLATINUM", new BigDecimal(platP));
                String intP = getVal(data, "内部会员价"); if (intP != null) currentPrices.put("INTERNAL", new BigDecimal(intP));

                skuLevelPriceMap.put(goods.getBarcode(), currentPrices);

                Map<String, BigDecimal> currentCoupons = new HashMap<>();
                if (isDualTrackBrand) {
                    for (Map.Entry<String, BigDecimal> entry : currentPrices.entrySet()) {
                        String levelCode = entry.getKey();
                        BigDecimal mPrice = entry.getValue();
                        BigDecimal autoCoupon = salePrice.subtract(mPrice);
                        if (autoCoupon.compareTo(BigDecimal.ZERO) < 0) autoCoupon = BigDecimal.ZERO;
                        currentCoupons.put(levelCode, autoCoupon);
                    }
                }
                skuLevelCouponMap.put(goods.getBarcode(), currentCoupons);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().doRead();

        if (parsedGoodsList.isEmpty()) {
            return "未发现有效数据。若有空行，已自动跳过 " + skipCount[0] + " 条。";
        }

        List<String> barcodes = parsedGoodsList.stream().map(GmsGoods::getBarcode).collect(Collectors.toList());
        Map<String, GmsGoods> existGoodsMap = new HashMap<>();
        if (!barcodes.isEmpty()) {
            List<GmsGoods> existList = gmsGoodsService.lambdaQuery().in(GmsGoods::getBarcode, barcodes).list();
            existGoodsMap = existList.stream().collect(Collectors.toMap(GmsGoods::getBarcode, g -> g));
        }

        List<GmsGoods> toSaveList = new ArrayList<>();
        List<GmsGoods> toUpdateList = new ArrayList<>();

        for (GmsGoods parsedGoods : parsedGoodsList) {
            GmsGoods existObj = existGoodsMap.get(parsedGoods.getBarcode());
            if (existObj != null) {
                parsedGoods.setId(existObj.getId());
                parsedGoods.setTenantId(existObj.getTenantId());

                if (!Objects.equals(existObj.getBrandId(), parsedGoods.getBrandId())) {
                    if (parsedGoods.getBrandId() != null) gmsBrandService.updateGoodsCount(parsedGoods.getBrandId(), 1);
                    if (existObj.getBrandId() != null) gmsBrandService.updateGoodsCount(existObj.getBrandId(), -1);
                }
                if (!Objects.equals(existObj.getCategoryId(), parsedGoods.getCategoryId())) {
                    if (parsedGoods.getCategoryId() != null) gmsGoodsCategoryService.updateGoodsCount(parsedGoods.getCategoryId(), 1);
                    if (existObj.getCategoryId() != null) gmsGoodsCategoryService.updateGoodsCount(existObj.getCategoryId(), -1);
                }
                toUpdateList.add(parsedGoods);
            } else {
                if (parsedGoods.getBrandId() != null) gmsBrandService.updateGoodsCount(parsedGoods.getBrandId(), 1);
                if (parsedGoods.getCategoryId() != null) gmsGoodsCategoryService.updateGoodsCount(parsedGoods.getCategoryId(), 1);
                toSaveList.add(parsedGoods);
            }
        }

        if (!toSaveList.isEmpty()) gmsGoodsService.saveBatch(toSaveList);
        if (!toUpdateList.isEmpty()) gmsGoodsService.updateBatchById(toUpdateList);

        List<GmsGoods> allProcessed = new ArrayList<>();
        allProcessed.addAll(toSaveList);
        allProcessed.addAll(toUpdateList);

        for (GmsGoods g : allProcessed) {
            Map<String, BigDecimal> prices = skuLevelPriceMap.get(g.getBarcode());
            Map<String, BigDecimal> coupons = skuLevelCouponMap.get(g.getBarcode());
            if (prices != null && !prices.isEmpty()) {
                saveLevelPrices(g.getId(), prices, coupons);
            }
        }

        String resultMsg = String.format("🎉 导入完成！成功新增 %d 条，更新 %d 条记录。", toSaveList.size(), toUpdateList.size());
        if (skipCount[0] > 0) resultMsg += String.format(" 发现并跳过 %d 条无效数据。", skipCount[0]);
        return resultMsg;
    }

    private void saveLevelPrices(Long skuId, Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        List<PosSkuLevelPrice> existList = posSkuLevelPriceMapper.selectList(new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, skuId));
        Map<String, PosSkuLevelPrice> existMap = existList.stream().collect(Collectors.toMap(PosSkuLevelPrice::getLevelId, p -> p));
        Set<String> newLevels = levelPrices != null ? levelPrices.keySet() : new HashSet<>();

        List<Long> toDeleteIds = existList.stream().filter(p -> !newLevels.contains(p.getLevelId())).map(PosSkuLevelPrice::getId).collect(Collectors.toList());
        if (!toDeleteIds.isEmpty()) posSkuLevelPriceMapper.deleteBatchIds(toDeleteIds);

        if (levelPrices != null) {
            levelPrices.forEach((levelId, price) -> {
                if (price != null) {
                    BigDecimal coupon = (levelCoupons != null && levelCoupons.containsKey(levelId)) ? levelCoupons.get(levelId) : BigDecimal.ZERO;
                    if (coupon == null) coupon = BigDecimal.ZERO;

                    if (existMap.containsKey(levelId)) {
                        PosSkuLevelPrice existObj = existMap.get(levelId);
                        existObj.setMemberPrice(price);
                        existObj.setMemberCoupon(coupon);
                        posSkuLevelPriceMapper.updateById(existObj);
                    } else {
                        PosSkuLevelPrice newObj = new PosSkuLevelPrice();
                        newObj.setSkuId(skuId);
                        newObj.setLevelId(levelId);
                        newObj.setMemberPrice(price);
                        newObj.setMemberCoupon(coupon);
                        posSkuLevelPriceMapper.insert(newObj);
                    }
                }
            });
        }
    }
}
