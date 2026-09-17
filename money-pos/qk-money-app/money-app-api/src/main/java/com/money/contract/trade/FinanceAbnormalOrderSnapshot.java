package com.money.contract.trade;

import java.math.BigDecimal;

/** One TRADE audit row classified by the established FIN risk predicates. */
public class FinanceAbnormalOrderSnapshot {
    private final String orderNo;
    private final String createTimeLabel;
    private final String cashierName;
    private final BigDecimal payAmount;
    private final BigDecimal costAmount;
    private final BigDecimal profitAmount;
    private final String riskType;

    public FinanceAbnormalOrderSnapshot(String orderNo, String createTimeLabel, String cashierName,
                                        BigDecimal payAmount, BigDecimal costAmount, BigDecimal profitAmount,
                                        String riskType) {
        this.orderNo = orderNo;
        this.createTimeLabel = createTimeLabel;
        this.cashierName = cashierName;
        this.payAmount = payAmount;
        this.costAmount = costAmount;
        this.profitAmount = profitAmount;
        this.riskType = riskType;
    }

    public String getOrderNo() { return orderNo; }
    public String getCreateTimeLabel() { return createTimeLabel; }
    public String getCashierName() { return cashierName; }
    public BigDecimal getPayAmount() { return payAmount; }
    public BigDecimal getCostAmount() { return costAmount; }
    public BigDecimal getProfitAmount() { return profitAmount; }
    public String getRiskType() { return riskType; }
}
