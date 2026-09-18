package com.money.contract.goods;

import java.math.BigDecimal;

/** Immutable per-day GMS procurement amount for FIN waterfall assembly. */
public final class FinanceWaterfallProcurementSnapshot {
    private final String date;
    private final BigDecimal procurementAmount;

    public FinanceWaterfallProcurementSnapshot(String date, BigDecimal procurementAmount) {
        this.date = date;
        this.procurementAmount = procurementAmount;
    }

    public String getDate() { return date; }
    public BigDecimal getProcurementAmount() { return procurementAmount; }
}
