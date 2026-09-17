package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceShiftDiscountSnapshot {
    private final BigDecimal manualDiscount;
    private final BigDecimal voucherDiscount;
    private final BigDecimal memberCouponPay;
    private final BigDecimal waivedCouponAmount;
    private final long voucherCount;
    private final BigDecimal refundAmount;
    public FinanceShiftDiscountSnapshot(BigDecimal manualDiscount, BigDecimal voucherDiscount, BigDecimal memberCouponPay,
                                        BigDecimal waivedCouponAmount, long voucherCount, BigDecimal refundAmount) {
        this.manualDiscount = manualDiscount; this.voucherDiscount = voucherDiscount;
        this.memberCouponPay = memberCouponPay; this.waivedCouponAmount = waivedCouponAmount;
        this.voucherCount = voucherCount; this.refundAmount = refundAmount;
    }
    public BigDecimal getManualDiscount() { return manualDiscount; }
    public BigDecimal getVoucherDiscount() { return voucherDiscount; }
    public BigDecimal getMemberCouponPay() { return memberCouponPay; }
    public BigDecimal getWaivedCouponAmount() { return waivedCouponAmount; }
    public long getVoucherCount() { return voucherCount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
}
