package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.CheckoutGoodsQuery;
import com.money.contract.goods.CheckoutGoodsSnapshot;
import com.money.entity.GmsGoods;
import com.money.entity.GmsGoodsCategory;
import com.money.entity.PosSkuLevelPrice;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import com.money.mapper.GmsGoodsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** GMS 对结账商品读侧契约的实现。 */
@Service
@RequiredArgsConstructor
class CheckoutGoodsQueryService implements CheckoutGoodsQuery {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsCategoryMapper gmsGoodsCategoryMapper;
    private final GmsGoodsPriceService gmsGoodsPriceService;

    @Override
    public Map<Long, CheckoutGoodsSnapshot> findByIds(Collection<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) return Collections.emptyMap();
        List<GmsGoods> goods = gmsGoodsMapper.selectBatchIds(goodsIds);
        if (goods.isEmpty()) return Collections.emptyMap();

        List<Long> categoryIds = goods.stream().map(GmsGoods::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Collections.emptyMap()
                : gmsGoodsCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(GmsGoodsCategory::getId, GmsGoodsCategory::getName));
        Map<Long, List<PosSkuLevelPrice>> pricesByGoods = gmsGoodsPriceService.getPriceMap(
                goods.stream().map(GmsGoods::getId).collect(Collectors.toList()));

        return goods.stream().collect(Collectors.toMap(GmsGoods::getId, item -> {
            Map<String, BigDecimal> levelPrices = new HashMap<>();
            Map<String, BigDecimal> levelCoupons = new HashMap<>();
            for (PosSkuLevelPrice price : pricesByGoods.getOrDefault(item.getId(), Collections.emptyList())) {
                levelPrices.put(price.getLevelId(), price.getMemberPrice());
                levelCoupons.put(price.getLevelId(), price.getMemberCoupon());
            }
            return new CheckoutGoodsSnapshot(item.getId(), item.getBrandId(), item.getCategoryId(),
                    categoryNames.get(item.getCategoryId()), item.getBarcode(), item.getName(), item.getPurchasePrice(),
                    item.getAvgCostPrice(), item.getSalePrice(), item.getVipPrice(), item.getStock(),
                    item.getIsDiscountParticipable(), item.getIsCombo(), levelPrices, levelCoupons);
        }));
    }
}
