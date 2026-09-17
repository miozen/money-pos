package com.money.contract.trade;

import java.math.BigDecimal;

/** Per-cashier audit aggregate used by the FIN risk-control cards. */
public class FinanceCashierRiskSnapshot {
    private final String cashierName;
    private final long orderCount;
    private final BigDecimal manualDiscountAmount;
    private final long refundCount;

    public FinanceCashierRiskSnapshot(String cashierName, long orderCount, BigDecimal manualDiscountAmount,
                                      long refundCount) {
        this.cashierName = cashierName;
        this.orderCount = orderCount;
        this.manualDiscountAmount = manualDiscountAmount;
        this.refundCount = refundCount;
    }

    public String getCashierName() { return cashierName; }
    public long getOrderCount() { return orderCount; }
    public BigDecimal getManualDiscountAmount() { return manualDiscountAmount; }
    public long getRefundCount() { return refundCount; }
}
