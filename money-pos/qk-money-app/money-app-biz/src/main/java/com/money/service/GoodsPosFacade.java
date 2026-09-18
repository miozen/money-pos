package com.money.service;

import com.money.contract.goods.LegacyPosGoodsSearchQuery;
import com.money.contract.goods.LegacyPosGoodsSearchSnapshot;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.entity.SysBrandConfig;
import com.money.mapper.SysBrandConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * POS 商品收银中台 (Facade 外观模式)
 * 职责：专门服务于收银台，负责跨领域(商品域、价格域、策略域)的数据组装与脏数据清洗
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsPosFacade {

    private final LegacyPosGoodsSearchQuery legacyPosGoodsSearchQuery;
    private final SysBrandConfigMapper sysBrandConfigMapper;

    /**
     * 核心：POS 收银台全能搜索与策略清洗
     */
    public List<GmsGoodsVO> posSearchGoods(String keyword) {
        List<LegacyPosGoodsSearchSnapshot> goodsList = legacyPosGoodsSearchQuery.searchForLegacyPos(keyword);

        if (goodsList == null || goodsList.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 提取品牌ID，批量拉取品牌定价策略网
        List<Long> brandIds = goodsList.stream().map(LegacyPosGoodsSearchSnapshot::getBrandId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, Boolean> brandCouponStrategyMap = new HashMap<>();

        if (!brandIds.isEmpty()) {
            List<SysBrandConfig> configs = sysBrandConfigMapper.selectList(
                    new LambdaQueryWrapper<SysBrandConfig>()
                            .in(SysBrandConfig::getBrand, brandIds.stream().map(String::valueOf).collect(Collectors.toList()))
            );
            for (SysBrandConfig config : configs) {
                brandCouponStrategyMap.put(config.getBrand(), config.getCouponEnabled() != null ? config.getCouponEnabled() : false);
            }
        }

        // 2. 组装吐给收银台的视图 (VO)，并执行绝对严格的清洗
        return goodsList.stream().map(goods -> {
            GmsGoodsVO vo = toGoodsVO(goods);

            // 判别该商品品牌是否开启了“会员券双轨模式”
            boolean isDualTrack = false;
            if (goods.getBrandId() != null) {
                isDualTrack = brandCouponStrategyMap.getOrDefault(String.valueOf(goods.getBrandId()), false);
            }

            Map<String, BigDecimal> lpMap = new HashMap<>();
            Map<String, BigDecimal> lcMap = new HashMap<>();

            for (Map.Entry<String, BigDecimal> price : goods.getLevelPrices().entrySet()) {
                lpMap.put(price.getKey(), price.getValue());

                // 🌟 核心拦截关卡：策略未开启时，强制抹零非法券额！
                if (isDualTrack) {
                    lcMap.put(price.getKey(), goods.getLevelCoupons().getOrDefault(price.getKey(), BigDecimal.ZERO));
                } else {
                    lcMap.put(price.getKey(), BigDecimal.ZERO);
                }
            }
            vo.setLevelPrices(lpMap);
            vo.setLevelCoupons(lcMap);

            return vo;
        }).collect(Collectors.toList());
    }

    private GmsGoodsVO toGoodsVO(LegacyPosGoodsSearchSnapshot goods) {
        GmsGoodsVO vo = new GmsGoodsVO();
        vo.setId(goods.getId());
        vo.setBrandId(goods.getBrandId());
        vo.setCategoryId(goods.getCategoryId());
        vo.setBarcode(goods.getBarcode());
        vo.setName(goods.getName());
        vo.setPinyin(goods.getPinyin());
        vo.setPic(goods.getPic());
        vo.setUnit(goods.getUnit());
        vo.setSize(goods.getSize());
        vo.setDescription(goods.getDescription());
        vo.setPurchasePrice(goods.getPurchasePrice());
        vo.setSalePrice(goods.getSalePrice());
        vo.setVipPrice(goods.getVipPrice());
        vo.setCoupon(goods.getCoupon());
        vo.setStock(goods.getStock());
        vo.setSales(goods.getSales());
        vo.setStatus(goods.getStatus());
        vo.setCreateTime(goods.getCreateTime());
        vo.setUpdateTime(goods.getUpdateTime());
        vo.setIsDiscountParticipable(goods.getIsDiscountParticipable());
        return vo;
    }
}
