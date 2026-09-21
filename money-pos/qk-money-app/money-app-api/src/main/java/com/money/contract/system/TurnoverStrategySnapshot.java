package com.money.contract.system;

public class TurnoverStrategySnapshot {
    private final Integer leadTimeDays;
    private final Integer targetStockDays;
    private final Integer deadStockDays;
    public TurnoverStrategySnapshot(Integer leadTimeDays, Integer targetStockDays, Integer deadStockDays) {
        this.leadTimeDays = leadTimeDays; this.targetStockDays = targetStockDays; this.deadStockDays = deadStockDays;
    }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public Integer getTargetStockDays() { return targetStockDays; }
    public Integer getDeadStockDays() { return deadStockDays; }
}
