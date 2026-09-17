package com.money.contract.trade;

import java.math.BigDecimal;

/** One TRADE-owned brand revenue value for the HOME pie chart. */
public class HomeBrandSalesSnapshot {

    private final String name;
    private final BigDecimal value;

    public HomeBrandSalesSnapshot(String name, BigDecimal value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getValue() {
        return value;
    }
}
