package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.LegacyPosGoodsSearchQuery;
import com.money.contract.goods.LegacyPosGoodsSearchSnapshot;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.mapper.GmsGoodsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** GMS 对历史 POS 商品搜索契约的实现。 */
@Service
@RequiredArgsConstructor
class LegacyPosGoodsSearchQueryService implements LegacyPosGoodsSearchQuery {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsPriceService gmsGoodsPriceService;

    @Override
    public List<LegacyPosGoodsSearchSnapshot> searchForLegacyPos(String keyword) {
        // Keep the legacy ungrouped predicate: barcode OR name OR (mnemonicCode AND SALE).
        List<GmsGoods> goods = gmsGoodsMapper.selectList(new LambdaQueryWrapper<GmsGoods>()
                .like(GmsGoods::getBarcode, keyword)
                .or().like(GmsGoods::getName, keyword)
                .or().like(GmsGoods::getMnemonicCode, keyword)
                .eq(GmsGoods::getStatus, "SALE"));
        if (goods.isEmpty()) {
            return new ArrayList<LegacyPosGoodsSearchSnapshot>();
        }

        Map<Long, List<PosSkuLevelPrice>> pricesByGoods = gmsGoodsPriceService.getPriceMap(
                goods.stream().map(GmsGoods::getId).collect(Collectors.toList()));
        return goods.stream().map(item -> new LegacyPosGoodsSearchSnapshot(
                item.getId(), item.getBrandId(), item.getCategoryId(), item.getBarcode(), item.getName(),
                item.getPinyin(), item.getPic(), item.getUnit(), item.getSize(), item.getDescription(),
                item.getPurchasePrice(), item.getSalePrice(), item.getVipPrice(), item.getCoupon(), item.getStock(),
                item.getSales(), item.getStatus(), item.getCreateTime(), item.getUpdateTime(),
                item.getIsDiscountParticipable(), levelPrices(pricesByGoods.get(item.getId())),
                levelCoupons(pricesByGoods.get(item.getId())))).collect(Collectors.toList());
    }

    private Map<String, BigDecimal> levelPrices(List<PosSkuLevelPrice> prices) {
        Map<String, BigDecimal> result = new HashMap<String, BigDecimal>();
        for (PosSkuLevelPrice price : prices == null ? new ArrayList<PosSkuLevelPrice>() : prices) {
            result.put(price.getLevelId(), price.getMemberPrice());
        }
        return result;
    }

    private Map<String, BigDecimal> levelCoupons(List<PosSkuLevelPrice> prices) {
        Map<String, BigDecimal> result = new HashMap<String, BigDecimal>();
        for (PosSkuLevelPrice price : prices == null ? new ArrayList<PosSkuLevelPrice>() : prices) {
            result.put(price.getLevelId(), price.getMemberCoupon() == null ? BigDecimal.ZERO : price.getMemberCoupon());
        }
        return result;
    }
}
