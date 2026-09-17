package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceTimeTrafficSnapshot {
    private final int timeKey;
    private final BigDecimal averageOrderCount;
    private final BigDecimal averageSalesAmount;
    private final long totalOrderCount;
    private final BigDecimal totalSalesAmount;

    public FinanceTimeTrafficSnapshot(int timeKey, BigDecimal averageOrderCount, BigDecimal averageSalesAmount,
                                      long totalOrderCount, BigDecimal totalSalesAmount) {
        this.timeKey = timeKey;
        this.averageOrderCount = averageOrderCount;
        this.averageSalesAmount = averageSalesAmount;
        this.totalOrderCount = totalOrderCount;
        this.totalSalesAmount = totalSalesAmount;
    }

    public int getTimeKey() { return timeKey; }
    public BigDecimal getAverageOrderCount() { return averageOrderCount; }
    public BigDecimal getAverageSalesAmount() { return averageSalesAmount; }
    public long getTotalOrderCount() { return totalOrderCount; }
    public BigDecimal getTotalSalesAmount() { return totalSalesAmount; }
}
