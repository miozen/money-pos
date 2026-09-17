package com.money.contract.trade;

import java.math.BigDecimal;

/** Today's order-side asset-dashboard totals. */
public class FinanceTodayAssetOrderMetricsSnapshot {
    private final BigDecimal finalSalesAmount;
    private final BigDecimal waivedCouponAmount;
    private final BigDecimal actualCouponDeduct;
    public FinanceTodayAssetOrderMetricsSnapshot(BigDecimal finalSalesAmount, BigDecimal waivedCouponAmount,
                                                  BigDecimal actualCouponDeduct) {
        this.finalSalesAmount = finalSalesAmount;
        this.waivedCouponAmount = waivedCouponAmount;
        this.actualCouponDeduct = actualCouponDeduct;
    }
    public BigDecimal getFinalSalesAmount() { return finalSalesAmount; }
    public BigDecimal getWaivedCouponAmount() { return waivedCouponAmount; }
    public BigDecimal getActualCouponDeduct() { return actualCouponDeduct; }
}
