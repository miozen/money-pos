package com.money.contract.trade;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily values used by FIN to calculate its non-negative refund trend. */
public class FinanceRefundBaseSnapshot {
    private final LocalDate date;
    private final BigDecimal payAmount;
    private final BigDecimal finalSalesAmount;
    public FinanceRefundBaseSnapshot(LocalDate date, BigDecimal payAmount, BigDecimal finalSalesAmount) {
        this.date = date;
        this.payAmount = payAmount;
        this.finalSalesAmount = finalSalesAmount;
    }
    public LocalDate getDate() { return date; }
    public BigDecimal getPayAmount() { return payAmount; }
    public BigDecimal getFinalSalesAmount() { return finalSalesAmount; }
}
