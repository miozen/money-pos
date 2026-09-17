package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceHourlyTrafficSnapshot {
    private final int hour;
    private final BigDecimal averageOrderCount;
    private final BigDecimal averageSalesAmount;
    private final long totalOrderCount;
    private final BigDecimal totalSalesAmount;

    public FinanceHourlyTrafficSnapshot(int hour, BigDecimal averageOrderCount, BigDecimal averageSalesAmount,
                                        long totalOrderCount, BigDecimal totalSalesAmount) {
        this.hour = hour;
        this.averageOrderCount = averageOrderCount;
        this.averageSalesAmount = averageSalesAmount;
        this.totalOrderCount = totalOrderCount;
        this.totalSalesAmount = totalSalesAmount;
    }

    public int getHour() { return hour; }
    public BigDecimal getAverageOrderCount() { return averageOrderCount; }
    public BigDecimal getAverageSalesAmount() { return averageSalesAmount; }
    public long getTotalOrderCount() { return totalOrderCount; }
    public BigDecimal getTotalSalesAmount() { return totalSalesAmount; }
}
