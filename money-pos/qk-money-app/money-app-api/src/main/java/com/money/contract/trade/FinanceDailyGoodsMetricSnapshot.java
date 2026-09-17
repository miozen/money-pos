package com.money.contract.trade;

public class FinanceDailyGoodsMetricSnapshot {
    private final String date;
    private final Long goodsId;
    private final String goodsName;
    private final long salesQuantity;

    public FinanceDailyGoodsMetricSnapshot(String date, Long goodsId, String goodsName, long salesQuantity) {
        this.date = date;
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.salesQuantity = salesQuantity;
    }

    public String getDate() { return date; }
    public Long getGoodsId() { return goodsId; }
    public String getGoodsName() { return goodsName; }
    public long getSalesQuantity() { return salesQuantity; }
}
