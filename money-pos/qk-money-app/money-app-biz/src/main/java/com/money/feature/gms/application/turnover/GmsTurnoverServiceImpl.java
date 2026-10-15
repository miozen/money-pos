package com.money.feature.gms.application.turnover;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.GmsGoods.TurnoverDataVO.*;
import com.money.contract.system.TurnoverStrategyQuery;
import com.money.contract.system.TurnoverStrategySnapshot;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverMapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsTurnoverWarningSnapshot;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsTurnoverWarningSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GmsTurnoverServiceImpl implements GmsTurnoverService {

    private final GmsTurnoverMapper gmsTurnoverMapper;
    private final TurnoverStrategyQuery turnoverStrategyQuery;
    private final GmsTurnoverWarningSnapshotMapper snapshotMapper;
    @Override
    public TurnoverDashboardVO getTurnoverWarnings() {
        List<WarningItemVO> rawList = gmsTurnoverMapper.scanAllGoodsTurnover();

        List<WarningItemVO> replenishList = new ArrayList<>();
        List<WarningItemVO> deadStockList = new ArrayList<>();

        TurnoverStrategySnapshot strategy = turnoverStrategyQuery.getTurnoverStrategy();
        int leadTimeDays = 3;
        int targetStockDays = 14;
        int deadStockThreshold = 60;

        if (strategy != null) {
            if (strategy.getLeadTimeDays() != null) leadTimeDays = strategy.getLeadTimeDays();
            if (strategy.getTargetStockDays() != null) targetStockDays = strategy.getTargetStockDays();
            if (strategy.getDeadStockDays() != null) deadStockThreshold = strategy.getDeadStockDays();
        }

        for (WarningItemVO item : rawList) {
            if (item.getCurrentStock() > 0 && item.getSales90Days() == 0) {
                item.setWarningType("DEAD_STOCK");
                if (item.getLastSaleTime() != null) {
                    LocalDateTime lastSale = LocalDateTime.parse(item.getLastSaleTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    item.setDeadDays((int) ChronoUnit.DAYS.between(lastSale, LocalDateTime.now()));
                } else {
                    item.setDeadDays(999);
                }

                if (item.getDeadDays() >= deadStockThreshold) {
                    deadStockList.add(item);
                }
                continue;
            }

            if (item.getSales30Days() > 0) {
                double dailyVelocity = item.getSales30Days() / 30.0;
                double safetyStock = dailyVelocity * leadTimeDays;

                if (item.getCurrentStock() <= safetyStock) {
                    item.setWarningType("REPLENISH");
                    int suggested = (int) Math.ceil((dailyVelocity * targetStockDays) - item.getCurrentStock());
                    item.setSuggestedQty(Math.max(suggested, 1));
                    replenishList.add(item);
                }
            }
        }

        TurnoverDashboardVO dashboard = new TurnoverDashboardVO();
        replenishList.sort((a, b) -> Integer.compare(a.getCurrentStock(), b.getCurrentStock()));
        deadStockList.sort((a, b) -> Integer.compare(b.getDeadDays(), a.getDeadDays()));

        dashboard.setReplenishList(replenishList);
        dashboard.setDeadStockList(deadStockList);

        return dashboard;
    }

    // ==========================================
    // 🌟 P0-5：提取近 30 天快照，分析出最高频的顽疾商品
    // ==========================================
    @Override
    public Map<String, Object> getWarningTrend() {
        LocalDate startDate = LocalDate.now().minusDays(30);
        List<GmsTurnoverWarningSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<GmsTurnoverWarningSnapshot>()
                        .ge(GmsTurnoverWarningSnapshot::getSnapshotDate, startDate)
                        .orderByAsc(GmsTurnoverWarningSnapshot::getSnapshotDate)
        );

        List<String> dates = new ArrayList<>();
        List<Integer> repCounts = new ArrayList<>();
        List<Integer> deadCounts = new ArrayList<>();

        Map<String, Integer> repFreqMap = new HashMap<>();
        Map<String, Integer> deadFreqMap = new HashMap<>();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd");

        for (GmsTurnoverWarningSnapshot s : snapshots) {
            dates.add(s.getSnapshotDate().format(dtf));
            repCounts.add(s.getReplenishCount());
            deadCounts.add(s.getDeadStockCount());

            if (StrUtil.isNotBlank(s.getTopReplenishGoodsJson())) {
                List<WarningItemVO> items = JSONUtil.toList(s.getTopReplenishGoodsJson(), WarningItemVO.class);
                for (WarningItemVO item : items) {
                    repFreqMap.put(item.getGoodsName(), repFreqMap.getOrDefault(item.getGoodsName(), 0) + 1);
                }
            }
            if (StrUtil.isNotBlank(s.getTopDeadStockGoodsJson())) {
                List<WarningItemVO> items = JSONUtil.toList(s.getTopDeadStockGoodsJson(), WarningItemVO.class);
                for (WarningItemVO item : items) {
                    deadFreqMap.put(item.getGoodsName(), deadFreqMap.getOrDefault(item.getGoodsName(), 0) + 1);
                }
            }
        }

        // 把 Map 转成有序的 List 丢给前端
        List<Map<String, Object>> topRepList = repFreqMap.entrySet().stream()
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("name", e.getKey()); m.put("count", e.getValue()); return m; })
                .sorted((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")))
                .limit(20).collect(Collectors.toList());

        List<Map<String, Object>> topDeadList = deadFreqMap.entrySet().stream()
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("name", e.getKey()); m.put("count", e.getValue()); return m; })
                .sorted((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")))
                .limit(20).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("replenishCounts", repCounts);
        result.put("deadStockCounts", deadCounts);
        result.put("topReplenishFreq", topRepList);
        result.put("topDeadStockFreq", topDeadList);

        return result;
    }
}
