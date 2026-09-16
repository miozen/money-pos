package com.money.feature.gms.application.product;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.PosGoodsCatalogQuery;
import com.money.contract.goods.PosGoodsCatalogSnapshot;
import com.money.entity.GmsGoods;
import com.money.entity.PosSkuLevelPrice;
import com.money.mapper.GmsGoodsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** GMS 对 POS 商品目录查询契约的实现。 */
@Service
@RequiredArgsConstructor
class PosGoodsCatalogQueryService implements PosGoodsCatalogQuery {

    private final GmsGoodsMapper gmsGoodsMapper;
    private final GmsGoodsPriceService gmsGoodsPriceService;

    @Override
    public List<PosGoodsCatalogSnapshot> searchForPos(String keyword) {
        List<GmsGoods> goods = gmsGoodsMapper.selectList(new LambdaQueryWrapper<GmsGoods>()
                .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper.like(GmsGoods::getBarcode, keyword)
                        .or().like(GmsGoods::getName, keyword)
                        .or().like(GmsGoods::getMnemonicCode, keyword.toUpperCase())));
        if (goods.isEmpty()) return new ArrayList<>();

        Map<Long, List<PosSkuLevelPrice>> pricesByGoods = gmsGoodsPriceService.getPriceMap(
                goods.stream().map(GmsGoods::getId).collect(Collectors.toList()));
        return goods.stream().map(item -> {
            Map<String, BigDecimal> levelPrices = new HashMap<>();
            Map<String, BigDecimal> levelCoupons = new HashMap<>();
            for (PosSkuLevelPrice price : pricesByGoods.getOrDefault(item.getId(), new ArrayList<>())) {
                levelPrices.put(price.getLevelId(), price.getMemberPrice());
                levelCoupons.put(price.getLevelId(), price.getMemberCoupon() != null ? price.getMemberCoupon() : BigDecimal.ZERO);
            }
            return new PosGoodsCatalogSnapshot(item.getId(), item.getBarcode(), item.getName(), item.getBrandId(),
                    item.getPurchasePrice(), item.getSalePrice(), item.getVipPrice(), item.getCoupon(), item.getStock(),
                    item.getStatus(), item.getIsDiscountParticipable(), item.getIsCombo(), levelPrices, levelCoupons);
        }).collect(Collectors.toList());
    }
}
