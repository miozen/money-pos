package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.mapper.PosSkuLevelPriceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领域服务：商品价格矩阵子域 (Domain Service)
 * 职责：专注处理多级会员价与双轨用券的平滑合并、查询与清理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmsGoodsPriceService {

    private final PosSkuLevelPriceMapper posSkuLevelPriceMapper;

    /**
     * 核心写入：平滑刷新价格矩阵 (Merge / Upsert)
     */
    public void saveLevelPrices(Long skuId, Map<String, BigDecimal> levelPrices, Map<String, BigDecimal> levelCoupons) {
        List<PosSkuLevelPrice> existList = posSkuLevelPriceMapper.selectList(
                new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, skuId)
        );
        Map<String, PosSkuLevelPrice> existMap = existList.stream().collect(Collectors.toMap(PosSkuLevelPrice::getLevelId, p -> p));

        Set<String> newLevels = levelPrices != null ? levelPrices.keySet() : new HashSet<>();

        // 剔除多余的旧配置
        List<Long> toDeleteIds = existList.stream()
                .filter(p -> !newLevels.contains(p.getLevelId()))
                .map(PosSkuLevelPrice::getId)
                .collect(Collectors.toList());
        if (!toDeleteIds.isEmpty()) posSkuLevelPriceMapper.deleteBatchIds(toDeleteIds);

        // 新增或更新
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

    /**
     * 核心读取：批量获取商品的价格矩阵
     */
    public Map<Long, List<PosSkuLevelPrice>> getPriceMap(List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) return new java.util.HashMap<>();
        List<PosSkuLevelPrice> allLevelPrices = posSkuLevelPriceMapper.selectList(
                new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds)
        );
        return allLevelPrices.stream().collect(Collectors.groupingBy(PosSkuLevelPrice::getSkuId));
    }

    /**
     * 核心清理：商品被删时，级联清理价格矩阵
     */
    public void deleteByGoodsIds(Set<Long> goodsIds) {
        if (goodsIds != null && !goodsIds.isEmpty()) {
            posSkuLevelPriceMapper.delete(new LambdaQueryWrapper<PosSkuLevelPrice>().in(PosSkuLevelPrice::getSkuId, goodsIds));
        }
    }
}
