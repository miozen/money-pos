package com.money.contract.trade;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TRADE-owned daily financial projection for member-benefit non-product receipts.
 * Product order counts, quantities and costs deliberately do not belong here.
 */
public class FinanceNonProductReceiptDailySnapshot {
    private final LocalDate date;
    private final BigDecimal incomeAmount;
    private final BigDecimal collectionAmount;
    private final BigDecimal refundAmount;

    public FinanceNonProductReceiptDailySnapshot(LocalDate date, BigDecimal incomeAmount,
                                                  BigDecimal collectionAmount, BigDecimal refundAmount) {
        this.date = date;
        this.incomeAmount = incomeAmount;
        this.collectionAmount = collectionAmount;
        this.refundAmount = refundAmount;
    }

    public LocalDate getDate() { return date; }
    public BigDecimal getIncomeAmount() { return incomeAmount; }
    public BigDecimal getCollectionAmount() { return collectionAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
}
