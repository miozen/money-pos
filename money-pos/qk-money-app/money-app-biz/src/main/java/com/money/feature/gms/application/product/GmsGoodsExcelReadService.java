package com.money.feature.gms.application.product;

import com.alibaba.excel.EasyExcel;
import com.money.entity.GmsBrand;
import com.money.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCategory;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.feature.gms.application.catalog.GmsBrandService;
import com.money.feature.gms.application.catalog.GmsGoodsCategoryService;
import com.money.service.SysDictDetailService;
import com.money.util.ExcelDropDownHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GMS 商品 Excel 的只读输出边界。
 *
 * <p>模板与导出必须共享动态会员价表头；导入仍由 {@link GmsGoodsExcelManager}
 * 负责，以保持其写入事务不受读模型迁移影响。</p>
 */
@Service
@RequiredArgsConstructor
public class GmsGoodsExcelReadService {

    private final GmsGoodsCategoryService gmsGoodsCategoryService;
    private final GmsBrandService gmsBrandService;
    private final SysDictDetailService sysDictDetailService;
    private final GmsGoodsService gmsGoodsService;
    private final GmsGoodsPriceService gmsGoodsPriceService;

    public void writeTemplate(HttpServletResponse response) throws IOException {
        List<List<String>> heads = buildDynamicHeads();
        Map<Integer, String[]> dropDownConfig = buildDropDownConfig();
        configureDownload(response, "智能商品导入模板");

        List<Object> demoRow = new ArrayList<>(Arrays.asList(
                "690123456789", "农夫山泉500ml", "", "", "上架 (SALE)", "允许", "瓶", "500ml", "2.00", "1.15", "100"));
        for (int i = 0; i < heads.size() - 11; i++) {
            demoRow.add("");
        }

        EasyExcel.write(response.getOutputStream())
                .head(heads)
                .registerWriteHandler(new ExcelDropDownHandler(dropDownConfig))
                .sheet("商品资料填写区")
                .doWrite(List.of(demoRow));
    }

    public void writeExport(HttpServletResponse response) throws IOException {
        List<List<String>> heads = buildDynamicHeads();
        List<GmsGoodsCategory> categories = gmsGoodsCategoryService.list();
        Map<Long, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(GmsGoodsCategory::getId, GmsGoodsCategory::getName));
        List<GmsBrand> brands = gmsBrandService.list();
        Map<Long, String> brandNames = brands.stream()
                .collect(Collectors.toMap(GmsBrand::getId, GmsBrand::getName));
        Map<String, String> memberTypes = getVipDictMap();

        List<GmsGoods> goodsList = gmsGoodsService.list();
        List<Long> goodsIds = goodsList.stream().map(GmsGoods::getId).collect(Collectors.toList());
        Map<Long, List<PosSkuLevelPrice>> priceMatrix = goodsIds.isEmpty()
                ? new HashMap<>()
                : gmsGoodsPriceService.getPriceMap(goodsIds);

        List<List<Object>> dataList = new ArrayList<>();
        for (GmsGoods goods : goodsList) {
            dataList.add(buildExportRow(goods, categoryNames, brandNames, memberTypes, priceMatrix));
        }

        configureDownload(response, "门店商品全量档案");
        EasyExcel.write(response.getOutputStream())
                .head(heads)
                .sheet("全量商品数据")
                .doWrite(dataList);
    }

    private List<Object> buildExportRow(GmsGoods goods, Map<Long, String> categoryNames,
                                        Map<Long, String> brandNames, Map<String, String> memberTypes,
                                        Map<Long, List<PosSkuLevelPrice>> priceMatrix) {
        List<Object> row = new ArrayList<>();
        row.add(goods.getBarcode());
        row.add(goods.getName());
        row.add(goods.getCategoryId() == null ? "" : categoryNames.getOrDefault(goods.getCategoryId(), ""));
        row.add(goods.getBrandId() == null ? "" : brandNames.getOrDefault(goods.getBrandId(), ""));
        row.add(isSaleStatus(goods.getStatus()) ? "上架 (SALE)" : "下架 (SOLD_OUT)");
        row.add(goods.getIsDiscountParticipable() != null && goods.getIsDiscountParticipable() == 1 ? "允许" : "禁止");
        row.add(goods.getUnit());
        row.add(goods.getSize());
        row.add(goods.getSalePrice());
        row.add(goods.getPurchasePrice());
        row.add(goods.getStock());

        Map<String, BigDecimal> prices = priceMatrix.getOrDefault(goods.getId(), List.of()).stream()
                .collect(Collectors.toMap(PosSkuLevelPrice::getLevelId, PosSkuLevelPrice::getMemberPrice, (first, ignored) -> first));
        for (String memberType : memberTypes.keySet()) {
            BigDecimal price = prices.get(memberType);
            row.add(price == null ? "" : price.toString());
        }
        return row;
    }

    private List<List<String>> buildDynamicHeads() {
        List<List<String>> heads = new ArrayList<>();
        heads.add(List.of("*商品条码(必填且唯一)"));
        heads.add(List.of("*商品名称(必填)"));
        heads.add(List.of("所属分类(请选择)"));
        heads.add(List.of("所属品牌(请选择)"));
        heads.add(List.of("商品状态(请选择)"));
        heads.add(List.of("参与满减(请选择)"));
        heads.add(List.of("单位(如:件)"));
        heads.add(List.of("规格(如:500g)"));
        heads.add(List.of("建议零售价"));
        heads.add(List.of("加权平均成本价"));
        heads.add(List.of("初始库存"));
        for (String memberTypeName : getVipDictMap().values()) {
            heads.add(List.of("[会员特价] " + memberTypeName));
        }
        return heads;
    }

    private Map<Integer, String[]> buildDropDownConfig() {
        Map<Integer, String[]> result = new HashMap<>();
        List<GmsGoodsCategory> categories = gmsGoodsCategoryService.list();
        if (!categories.isEmpty()) {
            result.put(2, categories.stream().map(GmsGoodsCategory::getName).toArray(String[]::new));
        }
        List<GmsBrand> brands = gmsBrandService.list();
        if (!brands.isEmpty()) {
            result.put(3, brands.stream().map(GmsBrand::getName).toArray(String[]::new));
        }
        result.put(4, new String[]{"上架 (SALE)", "下架 (SOLD_OUT)"});
        result.put(5, new String[]{"允许", "禁止"});
        return result;
    }

    private Map<String, String> getVipDictMap() {
        Map<String, String> memberTypes = new java.util.LinkedHashMap<>(
                sysDictDetailService.getValueToCnDescMap("memberType"));
        memberTypes.remove("MEMBER");
        return memberTypes;
    }

    private void configureDownload(HttpServletResponse response, String filename) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFilename = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encodedFilename + ".xlsx");
    }

    private boolean isSaleStatus(Object status) {
        String value = String.valueOf(status).toUpperCase();
        return "UP".equals(value) || "1".equals(value) || "TRUE".equals(value) || "SALE".equals(value);
    }
}
