package com.money.contract.trade;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily net collection grouped by the original payment method for a non-product member-benefit receipt. */
public class FinanceNonProductReceiptPaymentSnapshot {
    private final LocalDate date;
    private final String methodCode;
    private final String payTag;
    private final BigDecimal netAmount;

    public FinanceNonProductReceiptPaymentSnapshot(LocalDate date, String methodCode, String payTag, BigDecimal netAmount) {
        this.date = date;
        this.methodCode = methodCode;
        this.payTag = payTag;
        this.netAmount = netAmount;
    }

    public LocalDate getDate() { return date; }
    public String getMethodCode() { return methodCode; }
    public String getPayTag() { return payTag; }
    public BigDecimal getNetAmount() { return netAmount; }
}
