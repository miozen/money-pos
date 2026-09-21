package com.money.mapper;

import com.money.feature.sys.infrastructure.persistence.entity.SysStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysStrategyMapperIntegrationTest {

    @Autowired
    private SysStrategyMapper strategyMapper;

    @Test
    void mapperCrudUsesSysLocalStrategyEntity() {
        SysStrategy strategy = new SysStrategy();
        strategy.setTrafficOrderThreshold(new BigDecimal("1.00"));
        strategy.setTrafficValueThreshold(new BigDecimal("50.00"));
        strategy.setTurnoverLeadTime(3);
        strategy.setTurnoverTargetDays(14);
        strategy.setDeadStockDays(60);
        strategy.setWeeklyAnalysisDays(90);
        strategy.setMonthlyAnalysisDays(180);
        strategy.setTenantId(0L);

        assertThat(strategyMapper.insert(strategy)).isEqualTo(1);
        assertThat(strategy.getId()).isNotNull();

        SysStrategy persisted = strategyMapper.selectById(strategy.getId());
        assertThat(persisted).extracting(
                        SysStrategy::getTrafficOrderThreshold,
                        SysStrategy::getTrafficValueThreshold,
                        SysStrategy::getTurnoverLeadTime,
                        SysStrategy::getTurnoverTargetDays,
                        SysStrategy::getDeadStockDays,
                        SysStrategy::getWeeklyAnalysisDays,
                        SysStrategy::getMonthlyAnalysisDays,
                        SysStrategy::getTenantId)
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("50.00"), 3, 14, 60, 90, 180, 0L);

        strategy.setDeadStockDays(61);
        assertThat(strategyMapper.updateById(strategy)).isEqualTo(1);
        assertThat(strategyMapper.selectById(strategy.getId()).getDeadStockDays()).isEqualTo(61);
        assertThat(strategyMapper.deleteById(strategy.getId())).isEqualTo(1);
    }
}
