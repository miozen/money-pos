package com.money.contract.trade;

import java.math.BigDecimal;

/** TRADE-owned order aggregate used by HOME's comprehensive dashboard. */
public class HomeDashboardOrderSnapshot {

    private final long orderCount;
    private final BigDecimal saleCount;
    private final BigDecimal profit;

    public HomeDashboardOrderSnapshot(long orderCount, BigDecimal saleCount, BigDecimal profit) {
        this.orderCount = orderCount;
        this.saleCount = saleCount;
        this.profit = profit;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public BigDecimal getSaleCount() {
        return saleCount;
    }

    public BigDecimal getProfit() {
        return profit;
    }
}
