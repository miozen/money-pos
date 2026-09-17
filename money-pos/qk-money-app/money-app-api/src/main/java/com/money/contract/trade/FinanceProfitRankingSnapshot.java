package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceProfitRankingSnapshot {
    private final String goodsName;
    private final int totalQuantity;
    private final BigDecimal totalSales;
    private final BigDecimal totalProfit;
    public FinanceProfitRankingSnapshot(String goodsName, int totalQuantity, BigDecimal totalSales, BigDecimal totalProfit) {
        this.goodsName = goodsName; this.totalQuantity = totalQuantity; this.totalSales = totalSales; this.totalProfit = totalProfit;
    }
    public String getGoodsName() { return goodsName; }
    public int getTotalQuantity() { return totalQuantity; }
    public BigDecimal getTotalSales() { return totalSales; }
    public BigDecimal getTotalProfit() { return totalProfit; }
}
