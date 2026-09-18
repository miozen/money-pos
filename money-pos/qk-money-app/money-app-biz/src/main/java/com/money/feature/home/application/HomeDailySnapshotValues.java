package com.money.feature.home.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable HOME-owned values ready to be persisted as one daily summary. */
public final class HomeDailySnapshotValues {
    private final LocalDate recordDate;
    private final BigDecimal salesAmount;
    private final int orderCount;
    private final BigDecimal profitAmount;
    private final BigDecimal asp;
    private final BigDecimal inventoryValue;
    private final int newMemberCount;

    public HomeDailySnapshotValues(LocalDate recordDate, BigDecimal salesAmount, int orderCount,
                                   BigDecimal profitAmount, BigDecimal asp, BigDecimal inventoryValue,
                                   int newMemberCount) {
        this.recordDate = recordDate;
        this.salesAmount = salesAmount;
        this.orderCount = orderCount;
        this.profitAmount = profitAmount;
        this.asp = asp;
        this.inventoryValue = inventoryValue;
        this.newMemberCount = newMemberCount;
    }

    public LocalDate getRecordDate() { return recordDate; }
    public BigDecimal getSalesAmount() { return salesAmount; }
    public int getOrderCount() { return orderCount; }
    public BigDecimal getProfitAmount() { return profitAmount; }
    public BigDecimal getAsp() { return asp; }
    public BigDecimal getInventoryValue() { return inventoryValue; }
    public int getNewMemberCount() { return newMemberCount; }
}
