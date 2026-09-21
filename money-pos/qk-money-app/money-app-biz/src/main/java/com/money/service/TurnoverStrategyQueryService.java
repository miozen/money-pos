package com.money.service;

import com.money.contract.system.TurnoverStrategyQuery;
import com.money.contract.system.TurnoverStrategySnapshot;
import com.money.feature.sys.infrastructure.persistence.entity.SysStrategy;
import com.money.mapper.SysStrategyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TurnoverStrategyQueryService implements TurnoverStrategyQuery {
    private final SysStrategyMapper strategyMapper;
    @Override public TurnoverStrategySnapshot getTurnoverStrategy() {
        SysStrategy strategy = strategyMapper.getGlobalStrategy();
        return strategy == null ? new TurnoverStrategySnapshot(null, null, null)
                : new TurnoverStrategySnapshot(strategy.getTurnoverLeadTime(), strategy.getTurnoverTargetDays(), strategy.getDeadStockDays());
    }
}
