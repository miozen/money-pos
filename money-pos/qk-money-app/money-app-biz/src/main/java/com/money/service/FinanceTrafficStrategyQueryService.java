package com.money.service;

import com.money.contract.system.FinanceTrafficStrategyQuery;
import com.money.contract.system.FinanceTrafficStrategySnapshot;
import com.money.entity.SysStrategy;
import com.money.mapper.SysStrategyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** SYS implementation of the narrow global traffic strategy read. */
@Service
@RequiredArgsConstructor
class FinanceTrafficStrategyQueryService implements FinanceTrafficStrategyQuery {
    private final SysStrategyMapper strategyMapper;

    @Override
    public FinanceTrafficStrategySnapshot getTrafficStrategy() {
        SysStrategy strategy = strategyMapper.getGlobalStrategy();
        if (strategy == null) {
            return new FinanceTrafficStrategySnapshot(null, null, null, null);
        }
        return new FinanceTrafficStrategySnapshot(strategy.getTrafficOrderThreshold(), strategy.getTrafficValueThreshold(),
                strategy.getWeeklyAnalysisDays(), strategy.getMonthlyAnalysisDays());
    }
}
