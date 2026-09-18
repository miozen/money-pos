package com.money.feature.gms.application.turnover;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.TurnoverDataVO.WarningItemVO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsTurnoverWarningSnapshot;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverWarningSnapshotMapper;
import com.money.mapper.SysStrategyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsTurnoverSnapshotIntegrationTest {

    @Autowired
    private GmsTurnoverService turnoverService;
    @Autowired
    private GmsTurnoverWarningSnapshotMapper snapshotMapper;
    @MockBean
    private GmsTurnoverMapper turnoverMapper;
    @MockBean
    private SysStrategyMapper sysStrategyMapper;

    @BeforeEach
    void clearSnapshots() {
        snapshotMapper.delete(new LambdaQueryWrapper<GmsTurnoverWarningSnapshot>()
                .ge(GmsTurnoverWarningSnapshot::getSnapshotDate, LocalDate.now().minusDays(30)));
    }

    @Test
    void warningQueryUpsertsOneDailySnapshotAndTrendReadsPersistedJsonInDateOrder() {
        when(sysStrategyMapper.getGlobalStrategy()).thenReturn(null);
        when(turnoverMapper.scanAllGoodsTurnover()).thenReturn(Arrays.asList(
                replenishItem(1L, "replenish-one"), deadStockItem(2L, "dead-one")));

        turnoverService.getTurnoverWarnings();

        LocalDate today = LocalDate.now();
        assertThat(snapshotMapper.selectCount(new LambdaQueryWrapper<GmsTurnoverWarningSnapshot>()
                .eq(GmsTurnoverWarningSnapshot::getSnapshotDate, today))).isEqualTo(1L);
        GmsTurnoverWarningSnapshot first = snapshotFor(today);
        assertThat(first.getReplenishCount()).isEqualTo(1);
        assertThat(first.getDeadStockCount()).isEqualTo(1);
        assertThat(first.getTopReplenishGoodsJson()).contains("replenish-one");
        assertThat(first.getTopDeadStockGoodsJson()).contains("dead-one");

        when(turnoverMapper.scanAllGoodsTurnover()).thenReturn(Collections.emptyList());
        turnoverService.getTurnoverWarnings();

        GmsTurnoverWarningSnapshot updated = snapshotFor(today);
        assertThat(snapshotMapper.selectCount(new LambdaQueryWrapper<GmsTurnoverWarningSnapshot>()
                .eq(GmsTurnoverWarningSnapshot::getSnapshotDate, today))).isEqualTo(1L);
        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(updated.getReplenishCount()).isZero();
        assertThat(updated.getDeadStockCount()).isZero();
        assertThat(updated.getTopReplenishGoodsJson()).isEqualTo("[]");
        assertThat(updated.getTopDeadStockGoodsJson()).isEqualTo("[]");

        insertSnapshot(today.minusDays(2), 3, 4,
                "[{\"goodsName\":\"repeat-replenish\"}]",
                "[{\"goodsName\":\"repeat-dead\"}]");
        insertSnapshot(today.minusDays(1), 5, 6,
                "[{\"goodsName\":\"repeat-replenish\"}]",
                "[{\"goodsName\":\"other-dead\"}]");

        Map<String, Object> trend = turnoverService.getWarningTrend();
        List<String> dates = castList(trend.get("dates"));
        assertThat(dates).containsExactly(
                today.minusDays(2).format(DateTimeFormatter.ofPattern("MM-dd")),
                today.minusDays(1).format(DateTimeFormatter.ofPattern("MM-dd")),
                today.format(DateTimeFormatter.ofPattern("MM-dd")));
        assertThat(castList(trend.get("replenishCounts"))).containsExactly(3, 5, 0);
        assertThat(castList(trend.get("deadStockCounts"))).containsExactly(4, 6, 0);
        assertThat(castList(trend.get("topReplenishFreq"))).anySatisfy(item -> {
            Map<String, Object> frequency = castMap(item);
            assertThat(frequency).containsEntry("name", "repeat-replenish").containsEntry("count", 2);
        });
    }

    private WarningItemVO replenishItem(Long id, String name) {
        WarningItemVO item = new WarningItemVO();
        item.setGoodsId(id);
        item.setGoodsName(name);
        item.setCurrentStock(1);
        item.setSales30Days(30);
        item.setSales90Days(30);
        return item;
    }

    private WarningItemVO deadStockItem(Long id, String name) {
        WarningItemVO item = new WarningItemVO();
        item.setGoodsId(id);
        item.setGoodsName(name);
        item.setCurrentStock(5);
        item.setSales30Days(0);
        item.setSales90Days(0);
        return item;
    }

    private GmsTurnoverWarningSnapshot snapshotFor(LocalDate date) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<GmsTurnoverWarningSnapshot>()
                .eq(GmsTurnoverWarningSnapshot::getSnapshotDate, date));
    }

    private void insertSnapshot(LocalDate date, int replenishCount, int deadStockCount,
                                String replenishJson, String deadStockJson) {
        GmsTurnoverWarningSnapshot snapshot = new GmsTurnoverWarningSnapshot();
        snapshot.setSnapshotDate(date);
        snapshot.setReplenishCount(replenishCount);
        snapshot.setDeadStockCount(deadStockCount);
        snapshot.setTopReplenishGoodsJson(replenishJson);
        snapshot.setTopDeadStockGoodsJson(deadStockJson);
        snapshotMapper.insert(snapshot);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
