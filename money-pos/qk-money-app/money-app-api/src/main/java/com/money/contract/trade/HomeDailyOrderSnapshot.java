package com.money.contract.trade;

import java.math.BigDecimal;

/** TRADE-owned input values for one HOME daily summary. */
public class HomeDailyOrderSnapshot {

    private final int orderCount;
    private final BigDecimal salesAmount;
    private final BigDecimal costAmount;

    public HomeDailyOrderSnapshot(int orderCount, BigDecimal salesAmount, BigDecimal costAmount) {
        this.orderCount = orderCount;
        this.salesAmount = salesAmount;
        this.costAmount = costAmount;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public BigDecimal getSalesAmount() {
        return salesAmount;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }
}
