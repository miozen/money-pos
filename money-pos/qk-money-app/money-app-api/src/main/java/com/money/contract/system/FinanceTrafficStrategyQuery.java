package com.money.contract.system;

/** SYS-owned global traffic settings needed by FIN traffic analysis. */
public interface FinanceTrafficStrategyQuery {
    FinanceTrafficStrategySnapshot getTrafficStrategy();
}
