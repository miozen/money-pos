package com.money.contract.system;

/** SYS-owned turnover settings needed by GMS inventory turnover analysis. */
public interface TurnoverStrategyQuery {
    TurnoverStrategySnapshot getTurnoverStrategy();
}
