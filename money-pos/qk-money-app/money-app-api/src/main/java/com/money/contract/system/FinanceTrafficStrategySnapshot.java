package com.money.contract.system;

import java.math.BigDecimal;

public class FinanceTrafficStrategySnapshot {
    private final BigDecimal orderThreshold;
    private final BigDecimal valueThreshold;
    private final Integer weeklyAnalysisDays;
    private final Integer monthlyAnalysisDays;

    public FinanceTrafficStrategySnapshot(BigDecimal orderThreshold, BigDecimal valueThreshold,
                                          Integer weeklyAnalysisDays, Integer monthlyAnalysisDays) {
        this.orderThreshold = orderThreshold;
        this.valueThreshold = valueThreshold;
        this.weeklyAnalysisDays = weeklyAnalysisDays;
        this.monthlyAnalysisDays = monthlyAnalysisDays;
    }

    public BigDecimal getOrderThreshold() { return orderThreshold; }
    public BigDecimal getValueThreshold() { return valueThreshold; }
    public Integer getWeeklyAnalysisDays() { return weeklyAnalysisDays; }
    public Integer getMonthlyAnalysisDays() { return monthlyAnalysisDays; }
}
