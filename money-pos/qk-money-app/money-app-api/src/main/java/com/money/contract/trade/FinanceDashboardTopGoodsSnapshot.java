package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceDashboardTopGoodsSnapshot {
    private final Long goodsId;
    private final String goodsName;
    private final long salesQuantity;
    private final BigDecimal salesAmount;

    public FinanceDashboardTopGoodsSnapshot(Long goodsId, String goodsName, long salesQuantity, BigDecimal salesAmount) {
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.salesQuantity = salesQuantity;
        this.salesAmount = salesAmount;
    }

    public Long getGoodsId() { return goodsId; }
    public String getGoodsName() { return goodsName; }
    public long getSalesQuantity() { return salesQuantity; }
    public BigDecimal getSalesAmount() { return salesAmount; }
}
