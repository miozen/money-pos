package com.money.contract.trade;

import java.math.BigDecimal;

/** Immutable per-day TRADE financial input for FIN waterfall assembly. */
public final class FinanceWaterfallOrderSnapshot {
    private final String date;
    private final BigDecimal totalAmount;
    private final BigDecimal couponAmount;
    private final BigDecimal voucherAmount;
    private final BigDecimal manualDiscountAmount;
    private final BigDecimal payAmount;
    private final BigDecimal refundAmount;
    private final BigDecimal netIncome;

    public FinanceWaterfallOrderSnapshot(String date, BigDecimal totalAmount, BigDecimal couponAmount,
                                         BigDecimal voucherAmount, BigDecimal manualDiscountAmount,
                                         BigDecimal payAmount, BigDecimal refundAmount, BigDecimal netIncome) {
        this.date = date;
        this.totalAmount = totalAmount;
        this.couponAmount = couponAmount;
        this.voucherAmount = voucherAmount;
        this.manualDiscountAmount = manualDiscountAmount;
        this.payAmount = payAmount;
        this.refundAmount = refundAmount;
        this.netIncome = netIncome;
    }

    public String getDate() { return date; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getCouponAmount() { return couponAmount; }
    public BigDecimal getVoucherAmount() { return voucherAmount; }
    public BigDecimal getManualDiscountAmount() { return manualDiscountAmount; }
    public BigDecimal getPayAmount() { return payAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public BigDecimal getNetIncome() { return netIncome; }
}
