package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceShiftPaymentSnapshot {
    private final String methodCode;
    private final String payTag;
    private final BigDecimal netAmount;
    public FinanceShiftPaymentSnapshot(String methodCode, String payTag, BigDecimal netAmount) {
        this.methodCode = methodCode; this.payTag = payTag; this.netAmount = netAmount;
    }
    public String getMethodCode() { return methodCode; }
    public String getPayTag() { return payTag; }
    public BigDecimal getNetAmount() { return netAmount; }
}
