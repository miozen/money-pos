package com.money.feature.gms.application.product;

import com.money.contract.goods.InventoryValuationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** GMS-owned narrow read boundary for the current stock valuation formula. */
@Service
@RequiredArgsConstructor
class InventoryValuationQueryService implements InventoryValuationQuery {

    private final GmsGoodsStockService goodsStockService;

    @Override
    public BigDecimal getCurrentStockValue() {
        return goodsStockService.getCurrentStockValue();
    }
}
