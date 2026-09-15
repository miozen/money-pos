package com.money.service;

import cn.hutool.core.bean.BeanUtil;
import com.money.feature.gms.application.product.GmsGoodsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.entity.GmsGoods;
import com.money.entity.PosSkuLevelPrice;
import com.money.entity.SysBrandConfig;
import com.money.mapper.PosSkuLevelPriceMapper;
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

    private final GmsGoodsService gmsGoodsService;
    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;
    private final SysBrandConfigMapper sysBrandConfigMapper;

    /**
     * 核心：POS 收银台全能搜索与策略清洗
     */
    public List<GmsGoodsVO> posSearchGoods(String keyword) {
        // 1. 查出基础商品信息
        List<GmsGoods> goodsList = gmsGoodsService.lambdaQuery()
                .like(GmsGoods::getBarcode, keyword)
                .or().like(GmsGoods::getName, keyword)
                .or().like(GmsGoods::getMnemonicCode, keyword)
                .eq(GmsGoods::getStatus, "SALE")
                .list();

        if (goodsList == null || goodsList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> goodsIds = goodsList.stream().map(GmsGoods::getId).collect(Collectors.toList());

        // 2. 提取品牌ID，批量拉取品牌定价策略网
        List<Long> brandIds = goodsList.stream().map(GmsGoods::getBrandId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
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

        // 3. 查出底层会员价格矩阵（可能包含历史脏数据）
        List<PosSkuLevelPrice> allLevelPrices = posSkuLevelPriceMapper.selectList(
                new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds)
        );
        Map<Long, List<PosSkuLevelPrice>> priceMap = allLevelPrices.stream()
                .collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));

        // 4. 组装吐给收银台的视图 (VO)，并执行绝对严格的清洗
        return goodsList.stream().map(goods -> {
            GmsGoodsVO vo = new GmsGoodsVO();
            BeanUtil.copyProperties(goods, vo);

            // 判别该商品品牌是否开启了“会员券双轨模式”
            boolean isDualTrack = false;
            if (goods.getBrandId() != null) {
                isDualTrack = brandCouponStrategyMap.getOrDefault(String.valueOf(goods.getBrandId()), false);
            }

            Map<String, BigDecimal> lpMap = new HashMap<>();
            Map<String, BigDecimal> lcMap = new HashMap<>();

            List<PosSkuLevelPrice> prices = priceMap.get(goods.getId());
            if (prices != null) {
                for (PosSkuLevelPrice p : prices) {
                    lpMap.put(p.getLevelId(), p.getMemberPrice());

                    // 🌟 核心拦截关卡：策略未开启时，强制抹零非法券额！
                    if (isDualTrack) {
                        lcMap.put(p.getLevelId(), p.getMemberCoupon() != null ? p.getMemberCoupon() : BigDecimal.ZERO);
                    } else {
                        lcMap.put(p.getLevelId(), BigDecimal.ZERO);
                    }
                }
            }
            vo.setLevelPrices(lpMap);
            vo.setLevelCoupons(lcMap);

            return vo;
        }).collect(Collectors.toList());
    }
}
