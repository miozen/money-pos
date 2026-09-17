package com.money.contract.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable TRADE detail-price projection used by the FIN profit-audit page. */
public final class FinanceProfitAuditSnapshot {
    private final String orderNo;
    private final String goodsName;
    private final LocalDateTime createTime;
    private final BigDecimal salePrice;
    private final BigDecimal goodsPrice;
    private final BigDecimal purchasePrice;
    private final BigDecimal unitProfit;
    private final BigDecimal profitMargin;
    private final int missingCost;

    public FinanceProfitAuditSnapshot(String orderNo, String goodsName, LocalDateTime createTime,
                                      BigDecimal salePrice, BigDecimal goodsPrice, BigDecimal purchasePrice,
                                      BigDecimal unitProfit, BigDecimal profitMargin, int missingCost) {
        this.orderNo = orderNo;
        this.goodsName = goodsName;
        this.createTime = createTime;
        this.salePrice = salePrice;
        this.goodsPrice = goodsPrice;
        this.purchasePrice = purchasePrice;
        this.unitProfit = unitProfit;
        this.profitMargin = profitMargin;
        this.missingCost = missingCost;
    }

    public String getOrderNo() { return orderNo; }
    public String getGoodsName() { return goodsName; }
    public LocalDateTime getCreateTime() { return createTime; }
    public BigDecimal getSalePrice() { return salePrice; }
    public BigDecimal getGoodsPrice() { return goodsPrice; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public BigDecimal getUnitProfit() { return unitProfit; }
    public BigDecimal getProfitMargin() { return profitMargin; }
    public int getMissingCost() { return missingCost; }
}
