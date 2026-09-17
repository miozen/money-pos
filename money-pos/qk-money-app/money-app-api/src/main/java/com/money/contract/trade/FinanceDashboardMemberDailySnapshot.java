package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceDashboardMemberDailySnapshot {
    private final String date;
    private final boolean member;
    private final long orderCount;
    private final BigDecimal salesAmount;

    public FinanceDashboardMemberDailySnapshot(String date, boolean member, long orderCount, BigDecimal salesAmount) {
        this.date = date;
        this.member = member;
        this.orderCount = orderCount;
        this.salesAmount = salesAmount;
    }

    public String getDate() { return date; }
    public boolean isMember() { return member; }
    public long getOrderCount() { return orderCount; }
    public BigDecimal getSalesAmount() { return salesAmount; }
}
