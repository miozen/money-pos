package com.money.contract.trade;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily net payment aggregate grouped by payment method and tag. */
public class FinancePaymentSummarySnapshot {
    private final LocalDate date;
    private final String methodCode;
    private final String payTag;
    private final BigDecimal netAmount;

    public FinancePaymentSummarySnapshot(LocalDate date, String methodCode, String payTag, BigDecimal netAmount) {
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
