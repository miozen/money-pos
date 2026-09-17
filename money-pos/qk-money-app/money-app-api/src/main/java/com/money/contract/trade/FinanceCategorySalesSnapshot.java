package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceCategorySalesSnapshot {
    private final Long categoryId;
    private final long salesQuantity;
    private final BigDecimal salesAmount;

    public FinanceCategorySalesSnapshot(Long categoryId, long salesQuantity, BigDecimal salesAmount) {
        this.categoryId = categoryId;
        this.salesQuantity = salesQuantity;
        this.salesAmount = salesAmount;
    }

    public Long getCategoryId() { return categoryId; }
    public long getSalesQuantity() { return salesQuantity; }
    public BigDecimal getSalesAmount() { return salesAmount; }
}
