package com.money.contract.trade;

import java.math.BigDecimal;

/** Read-only order aggregate used by HOME dashboard counts. */
public class HomeOrderReadSnapshot {

    private final long orderCount;
    private final BigDecimal saleCount;
    private final BigDecimal costCount;
    private final BigDecimal profit;

    public HomeOrderReadSnapshot(long orderCount, BigDecimal saleCount, BigDecimal costCount, BigDecimal profit) {
        this.orderCount = orderCount;
        this.saleCount = saleCount;
        this.costCount = costCount;
        this.profit = profit;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public BigDecimal getSaleCount() {
        return saleCount;
    }

    public BigDecimal getCostCount() {
        return costCount;
    }

    public BigDecimal getProfit() {
        return profit;
    }
}
