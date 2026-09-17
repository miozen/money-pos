package com.money.contract.trade;

import java.math.BigDecimal;

/** One TRADE-owned point in the HOME sales trend chart. */
public class HomeSalesTrendSnapshot {

    private final String date;
    private final BigDecimal sales;
    private final BigDecimal profit;

    public HomeSalesTrendSnapshot(String date, BigDecimal sales, BigDecimal profit) {
        this.date = date;
        this.sales = sales;
        this.profit = profit;
    }

    public String getDate() {
        return date;
    }

    public BigDecimal getSales() {
        return sales;
    }

    public BigDecimal getProfit() {
        return profit;
    }
}
