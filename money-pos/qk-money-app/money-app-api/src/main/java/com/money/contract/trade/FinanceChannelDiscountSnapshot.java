package com.money.contract.trade;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily member-coupon and voucher deductions for FIN channel analysis. */
public class FinanceChannelDiscountSnapshot {
    private final LocalDate date;
    private final BigDecimal actualCouponDeduct;
    private final BigDecimal useVoucherAmount;
    public FinanceChannelDiscountSnapshot(LocalDate date, BigDecimal actualCouponDeduct, BigDecimal useVoucherAmount) {
        this.date = date;
        this.actualCouponDeduct = actualCouponDeduct;
        this.useVoucherAmount = useVoucherAmount;
    }
    public LocalDate getDate() { return date; }
    public BigDecimal getActualCouponDeduct() { return actualCouponDeduct; }
    public BigDecimal getUseVoucherAmount() { return useVoucherAmount; }
}
