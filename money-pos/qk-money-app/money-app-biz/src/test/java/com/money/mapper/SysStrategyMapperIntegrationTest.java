package com.money.mapper;

import com.money.feature.sys.infrastructure.persistence.entity.SysStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DuplicateKeyException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysStrategyMapperIntegrationTest {

    @Autowired
    private SysStrategyMapper strategyMapper;

    @Test
    void globalStrategyTableRejectsASecondRow() {
        SysStrategy strategy = new SysStrategy();
        strategy.setTrafficOrderThreshold(new BigDecimal("1.00"));
        strategy.setTrafficValueThreshold(new BigDecimal("50.00"));
        strategy.setTurnoverLeadTime(3);
        strategy.setTurnoverTargetDays(14);
        strategy.setDeadStockDays(60);
        strategy.setWeeklyAnalysisDays(90);
        strategy.setMonthlyAnalysisDays(180);
        strategy.setTenantId(0L);

        assertThat(strategyMapper.getGlobalStrategy()).isNotNull();
        assertThatThrownBy(() -> strategyMapper.insert(strategy)).isInstanceOf(DuplicateKeyException.class);
    }
}
