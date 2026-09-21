package com.money.service.impl;

import com.money.feature.sys.infrastructure.persistence.entity.SysStrategy;
import com.money.mapper.SysStrategyMapper;
import com.money.service.SysStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysStrategyServiceImpl implements SysStrategyService {

    private static final Long GLOBAL_TENANT_ID = 0L;

    private final SysStrategyMapper sysStrategyMapper;

    @Override
    public SysStrategy getGlobalStrategy() {
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        return strategy != null ? strategy : new SysStrategy();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGlobalStrategy(SysStrategy strategy) {
        SysStrategy existing = sysStrategyMapper.getGlobalStrategy();
        if (existing != null) {
            strategy.setId(existing.getId());
            sysStrategyMapper.updateById(strategy);
            return;
        }

        strategy.setTenantId(GLOBAL_TENANT_ID);
        sysStrategyMapper.insert(strategy);
    }
}
