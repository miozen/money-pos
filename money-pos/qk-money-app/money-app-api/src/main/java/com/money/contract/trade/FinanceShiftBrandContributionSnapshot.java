package com.money.contract.trade;

import java.math.BigDecimal;

/** TRADE order-detail contribution keyed by the GMS-owned brand identifier. */
public class FinanceShiftBrandContributionSnapshot {
    private final String brandId;
    private final BigDecimal revenue;
    private final BigDecimal couponConsumption;
    public FinanceShiftBrandContributionSnapshot(String brandId, BigDecimal revenue, BigDecimal couponConsumption) {
        this.brandId = brandId; this.revenue = revenue; this.couponConsumption = couponConsumption;
    }
    public String getBrandId() { return brandId; }
    public BigDecimal getRevenue() { return revenue; }
    public BigDecimal getCouponConsumption() { return couponConsumption; }
}
