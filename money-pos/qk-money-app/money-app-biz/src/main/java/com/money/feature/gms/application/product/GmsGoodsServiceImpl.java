package com.money.feature.gms.application.product;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.dto.GmsGoods.GmsGoodsComboDTO;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.dto.GmsGoods.GmsGoodsQueryDTO;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.mapper.GmsGoodsMapper;
import com.money.feature.gms.application.catalog.GmsBrandService;
import com.money.feature.gms.application.catalog.GmsGoodsCategoryService;
import com.money.util.PageUtil;
import com.money.utils.PinyinUtil;
import com.money.web.exception.BaseException;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品领域 核心枢纽 (Application Orchestrator)
 * 🌟 极致解耦：只负责基础主档数据处理与四大子域的事务协调调度
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GmsGoodsServiceImpl extends ServiceImpl<GmsGoodsMapper, GmsGoods> implements GmsGoodsService {

    // 基础域依赖
    private final GmsBrandService gmsBrandService;
    private final GmsGoodsCategoryService gmsGoodsCategoryService;

    // 🌟 引入三大高级子域服务
    private final GmsGoodsPriceService goodsPriceService;
    private final GmsGoodsComboService goodsComboService;
    private final GmsGoodsStockService goodsStockService;

    @Override
    public PageVO<GmsGoodsVO> list(GmsGoodsQueryDTO queryDTO) {
        LambdaQueryChainWrapper<GmsGoods> queryChainWrapper = this.lambdaQuery();
        if (queryDTO.getCategoryId() != null) {
            List<Long> categoryIds = gmsGoodsCategoryService.getAllSubId(queryDTO.getCategoryId());
            queryChainWrapper.in(GmsGoods::getCategoryId, categoryIds);
        }
        Page<GmsGoods> page = queryChainWrapper
                .eq(ObjectUtil.isNotNull(queryDTO.getBrandId()), GmsGoods::getBrandId, queryDTO.getBrandId())
                .eq(ObjectUtil.isNotNull(queryDTO.getStatus()), GmsGoods::getStatus, queryDTO.getStatus())
                .eq(ObjectUtil.isNotNull(queryDTO.getIsCombo()), GmsGoods::getIsCombo, queryDTO.getIsCombo())
                .like(StrUtil.isNotBlank(queryDTO.getBarcode()), GmsGoods::getBarcode, queryDTO.getBarcode())
                .like(StrUtil.isNotBlank(queryDTO.getName()), GmsGoods::getName, queryDTO.getName())
                .orderByDesc(StrUtil.isBlank(queryDTO.getOrderBy()), GmsGoods::getUpdateTime)
                .last(StrUtil.isNotBlank(queryDTO.getOrderBy()), queryDTO.getOrderBySql())
                .page(PageUtil.toPage(queryDTO));

        PageVO<GmsGoodsVO> pageVO = PageUtil.toPageVO(page, GmsGoodsVO::new);

        if (!pageVO.getRecords().isEmpty()) {
            List<Long> goodsIds = pageVO.getRecords().stream().map(GmsGoodsVO::getId).collect(Collectors.toList());

            // 1. 委托【价格子域】拉取矩阵
            Map<Long, List<PosSkuLevelPrice>> priceMap = goodsPriceService.getPriceMap(goodsIds);

            // 2. 委托【套餐子域】拉取组合明细
            Map<Long, List<GmsGoodsCombo>> comboMap = goodsComboService.getComboMap(goodsIds);

            // 3. 提取子商品名称 (属于主档域自身能力)
            List<Long> allSubIds = comboMap.values().stream().flatMap(List::stream).map(GmsGoodsCombo::getSubGoodsId).distinct().collect(Collectors.toList());
            Map<Long, String> subNameMap = allSubIds.isEmpty() ? new HashMap<>() :
                    this.listByIds(allSubIds).stream().collect(Collectors.toMap(GmsGoods::getId, GmsGoods::getName));

            // 4. 数据装配
            for (GmsGoodsVO vo : pageVO.getRecords()) {
                List<PosSkuLevelPrice> prices = priceMap.get(vo.getId());
                Map<String, BigDecimal> lpMap = new HashMap<>();
                Map<String, BigDecimal> lcMap = new HashMap<>();
                if (prices != null) {
                    for (PosSkuLevelPrice p : prices) {
                        lpMap.put(p.getLevelId(), p.getMemberPrice());
                        lcMap.put(p.getLevelId(), p.getMemberCoupon());
                    }
                }
                vo.setLevelPrices(lpMap);
                vo.setLevelCoupons(lcMap);

                List<GmsGoodsCombo> mySubItems = comboMap.get(vo.getId());
                if (mySubItems != null && !mySubItems.isEmpty()) {
                    String desc = mySubItems.stream()
                            .map(c -> subNameMap.getOrDefault(c.getSubGoodsId(), "未知") + "x" + c.getSubGoodsQty())
                            .collect(Collectors.joining(", "));
                    vo.setComboDesc(desc);

                    List<GmsGoodsComboDTO> dtoList = mySubItems.stream().map(c -> {
                        GmsGoodsComboDTO dto = new GmsGoodsComboDTO();
                        dto.setSubGoodsId(c.getSubGoodsId());
                        dto.setSubGoodsQty(c.getSubGoodsQty());
                        return dto;
                    }).collect(Collectors.toList());
                    vo.setSubGoodsList(dtoList);
                }
            }
        }
        return pageVO;
    }

    @Override
    public void add(GmsGoodsDTO addDTO, MultipartFile pic) {
        boolean exists = this.lambdaQuery().eq(GmsGoods::getBarcode, addDTO.getBarcode()).exists();
        if (exists) throw new BaseException("条码已存在");

        // 1. 处理核心主档数据
        GmsGoods gmsGoods = new GmsGoods();
        BeanUtil.copyProperties(addDTO, gmsGoods);
        gmsGoods.setIsCombo(addDTO.getIsCombo() == null ? 0 : addDTO.getIsCombo());
        if (StrUtil.isNotBlank(gmsGoods.getName())) {
            gmsGoods.setMnemonicCode(PinyinUtil.getFirstLetter(gmsGoods.getName()));
        }
        gmsBrandService.updateGoodsCount(gmsGoods.getBrandId(), 1);
        gmsGoodsCategoryService.updateGoodsCount(gmsGoods.getCategoryId(), 1);

        // 主档落库生成 ID
        this.save(gmsGoods);

        // 🌟 2. 委托【价格子域】挂载会员价与券
        goodsPriceService.saveLevelPrices(gmsGoods.getId(), addDTO.getLevelPrices(), addDTO.getLevelCoupons());

        // 🌟 3. 委托【套餐子域】挂载组合明细
        goodsComboService.saveComboDetails(gmsGoods.getId(), addDTO.getIsCombo(), addDTO.getSubGoodsList());
    }

    @Override
    public void update(GmsGoodsDTO updateDTO, MultipartFile pic) {
        boolean exists = this.lambdaQuery().ne(GmsGoods::getId, updateDTO.getId()).eq(GmsGoods::getBarcode, updateDTO.getBarcode()).exists();
        if (exists) throw new BaseException("条码已存在");

        GmsGoods gmsGoods = this.getById(updateDTO.getId());
        Integer originalIsCombo = gmsGoods.getIsCombo(); // 🌟 抓取商品在数据库里最真实的原始身份

        // 1. 处理分类/品牌商品计数调整
        if (!Objects.equals(gmsGoods.getBrandId(), updateDTO.getBrandId())) {
            gmsBrandService.updateGoodsCount(updateDTO.getBrandId(), 1);
            gmsBrandService.updateGoodsCount(gmsGoods.getBrandId(), -1);
        }
        if (!Objects.equals(gmsGoods.getCategoryId(), updateDTO.getCategoryId())) {
            gmsGoodsCategoryService.updateGoodsCount(updateDTO.getCategoryId(), 1);
            gmsGoodsCategoryService.updateGoodsCount(gmsGoods.getCategoryId(), -1);
        }

        BeanUtil.copyProperties(updateDTO, gmsGoods);

        // ==========================================
        // 🌟 方案 A：极致坚固的后端防降级与防篡改护城河
        // ==========================================
        boolean skipComboWipeout = false;

        if (originalIsCombo != null && originalIsCombo == 1) {
            // 铁律 1：曾经是套餐，永远是套餐。无视前端瞎传的 isCombo 参数，强制锁死！
            gmsGoods.setIsCombo(1);

            // 铁律 2：如果传过来的子商品配方为空，说明这是个“修改库存/名称”的快捷动作
            if (updateDTO.getSubGoodsList() == null || updateDTO.getSubGoodsList().isEmpty()) {
                skipComboWipeout = true; // 打上保护标记：禁止清空配方！
            }
        } else {
            // 原来不是套餐，走普通商品逻辑
            if (gmsGoods.getIsCombo() == null) gmsGoods.setIsCombo(0);
        }

        if (StrUtil.isNotBlank(gmsGoods.getName())) {
            gmsGoods.setMnemonicCode(PinyinUtil.getFirstLetter(gmsGoods.getName()));
        }

        // 更新核心主档
        this.updateById(gmsGoods);

        // 2. 委托【价格子域】执行策略融合 (Upsert)
        goodsPriceService.saveLevelPrices(gmsGoods.getId(), updateDTO.getLevelPrices(), updateDTO.getLevelCoupons());

        // 3. 委托【套餐子域】执行 BOM 重构
        if (!skipComboWipeout) {
            // 正常携带了配方数据，放行重构
            goodsComboService.saveComboDetails(gmsGoods.getId(), gmsGoods.getIsCombo(), updateDTO.getSubGoodsList());
        } else {
            // 拦截清空操作，保护底层资产！
            log.info("【系统防卫】拦截到套餐「{}」的快捷信息更新，已智能跳过底层配方覆写，防止幽灵降级！", gmsGoods.getName());
        }
    }

    @Override
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return;

        // 1. 删除主商品
        this.removeByIds(ids);

        // 2. 级联委托销毁
        goodsPriceService.deleteByGoodsIds(ids);
        goodsComboService.deleteByGoodsIds(ids);
    }

    @Override
    public void sell(Long goodsId, Integer qty) {
        // POS 扣款入口预留
    }

    @Override
    public void updateStock(Long goodsId, Integer qty) {
        goodsStockService.updateStock(goodsId, qty);
    }

    @Override
    public BigDecimal getCurrentStockValue() {
        return goodsStockService.getCurrentStockValue();
    }
}
