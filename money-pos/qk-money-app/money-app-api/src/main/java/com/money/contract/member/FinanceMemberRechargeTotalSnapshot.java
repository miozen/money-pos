package com.money.contract.member;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Per-day real-cash total for the legacy RECHARGE and REVERSAL trend. */
public class FinanceMemberRechargeTotalSnapshot {

    private final LocalDate date;
    private final BigDecimal totalAmount;

    public FinanceMemberRechargeTotalSnapshot(LocalDate date, BigDecimal totalAmount) {
        this.date = date;
        this.totalAmount = totalAmount;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
