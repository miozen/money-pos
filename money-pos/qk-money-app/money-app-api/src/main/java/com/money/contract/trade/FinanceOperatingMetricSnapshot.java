package com.money.contract.trade;

import java.math.BigDecimal;

/** Immutable TRADE period aggregate; FIN owns all presentation calculations. */
public class FinanceOperatingMetricSnapshot {
    private final String period;
    private final long orderCount;
    private final long goodsCount;
    private final BigDecimal netSalesAmount;
    private final BigDecimal costAmount;

    public FinanceOperatingMetricSnapshot(String period, long orderCount, long goodsCount,
                                          BigDecimal netSalesAmount, BigDecimal costAmount) {
        this.period = period;
        this.orderCount = orderCount;
        this.goodsCount = goodsCount;
        this.netSalesAmount = netSalesAmount;
        this.costAmount = costAmount;
    }

    public String getPeriod() { return period; }
    public long getOrderCount() { return orderCount; }
    public long getGoodsCount() { return goodsCount; }
    public BigDecimal getNetSalesAmount() { return netSalesAmount; }
    public BigDecimal getCostAmount() { return costAmount; }
}
