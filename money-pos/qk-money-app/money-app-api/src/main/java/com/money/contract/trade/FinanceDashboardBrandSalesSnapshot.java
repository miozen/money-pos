package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceDashboardBrandSalesSnapshot {
    private final Long brandId;
    private final BigDecimal salesAmount;

    public FinanceDashboardBrandSalesSnapshot(Long brandId, BigDecimal salesAmount) {
        this.brandId = brandId;
        this.salesAmount = salesAmount;
    }

    public Long getBrandId() { return brandId; }
    public BigDecimal getSalesAmount() { return salesAmount; }
}
