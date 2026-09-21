package com.money.feature.gms.application.turnover;

import com.money.dto.GmsGoods.TurnoverDataVO.TurnoverDashboardVO;
import com.money.dto.GmsGoods.TurnoverDataVO.WarningItemVO;
import com.money.contract.system.TurnoverStrategyQuery;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverWarningSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GmsTurnoverServiceImplTest {

    @Test
    void snapshotPersistenceFailureDoesNotBlockWarningResponse() {
        GmsTurnoverMapper turnoverMapper = mock(GmsTurnoverMapper.class);
        TurnoverStrategyQuery strategyQuery = mock(TurnoverStrategyQuery.class);
        GmsTurnoverWarningSnapshotMapper snapshotMapper = mock(GmsTurnoverWarningSnapshotMapper.class);
        WarningItemVO replenish = new WarningItemVO();
        replenish.setGoodsId(1L);
        replenish.setGoodsName("replenish");
        replenish.setCurrentStock(1);
        replenish.setSales30Days(30);
        replenish.setSales90Days(30);
        when(turnoverMapper.scanAllGoodsTurnover()).thenReturn(Collections.singletonList(replenish));
        when(snapshotMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("snapshot unavailable"));

        TurnoverDashboardVO dashboard = new GmsTurnoverServiceImpl(turnoverMapper, strategyQuery, snapshotMapper)
                .getTurnoverWarnings();

        assertThat(dashboard.getReplenishList()).singleElement().extracting(WarningItemVO::getGoodsName)
                .isEqualTo("replenish");
        assertThat(dashboard.getDeadStockList()).isEmpty();
    }
}
