package com.money.contract.trade;

import java.math.BigDecimal;

/** Per-order amounts needed by FIN's established core-metric formula. */
public class FinanceDailyOrderMetricSnapshot {
    private final BigDecimal totalAmount;
    private final BigDecimal couponAmount;
    private final BigDecimal actualCouponDeduct;
    private final BigDecimal waivedCouponAmount;
    private final BigDecimal useVoucherAmount;
    private final BigDecimal manualDiscountAmount;
    private final BigDecimal payAmount;
    private final BigDecimal finalSalesAmount;
    private final BigDecimal costAmount;

    public FinanceDailyOrderMetricSnapshot(BigDecimal totalAmount, BigDecimal couponAmount,
                                           BigDecimal actualCouponDeduct, BigDecimal waivedCouponAmount,
                                           BigDecimal useVoucherAmount, BigDecimal manualDiscountAmount,
                                           BigDecimal payAmount, BigDecimal finalSalesAmount, BigDecimal costAmount) {
        this.totalAmount = totalAmount;
        this.couponAmount = couponAmount;
        this.actualCouponDeduct = actualCouponDeduct;
        this.waivedCouponAmount = waivedCouponAmount;
        this.useVoucherAmount = useVoucherAmount;
        this.manualDiscountAmount = manualDiscountAmount;
        this.payAmount = payAmount;
        this.finalSalesAmount = finalSalesAmount;
        this.costAmount = costAmount;
    }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getCouponAmount() { return couponAmount; }
    public BigDecimal getActualCouponDeduct() { return actualCouponDeduct; }
    public BigDecimal getWaivedCouponAmount() { return waivedCouponAmount; }
    public BigDecimal getUseVoucherAmount() { return useVoucherAmount; }
    public BigDecimal getManualDiscountAmount() { return manualDiscountAmount; }
    public BigDecimal getPayAmount() { return payAmount; }
    public BigDecimal getFinalSalesAmount() { return finalSalesAmount; }
    public BigDecimal getCostAmount() { return costAmount; }
}
