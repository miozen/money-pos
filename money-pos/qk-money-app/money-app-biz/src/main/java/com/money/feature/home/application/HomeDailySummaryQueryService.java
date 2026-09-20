package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.home.infrastructure.persistence.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** HOME-only query access to its daily-summary read model. */
@Service
@RequiredArgsConstructor
public class HomeDailySummaryQueryService {
    private final OmsDailySummaryMapper dailySummaryMapper;
    private final JdbcTemplate jdbcTemplate;

    public boolean exists(LocalDate date) {
        return dailySummaryMapper.exists(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, date));
    }

    public OmsDailySummary getByDate(LocalDate date) {
        return dailySummaryMapper.selectOne(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, date).last("LIMIT 1"));
    }

    /** Keeps the read-only dashboard available if startup or a scheduled refresh has failed. */
    public OmsDailySummary getByDateOrEmpty(LocalDate date) {
        OmsDailySummary summary = getByDate(date);
        if (summary != null) {
            return summary;
        }
        OmsDailySummary empty = new OmsDailySummary();
        empty.setRecordDate(date);
        empty.setSalesAmount(BigDecimal.ZERO);
        empty.setOrderCount(0);
        empty.setProfitAmount(BigDecimal.ZERO);
        empty.setAsp(BigDecimal.ZERO);
        empty.setInventoryValue(BigDecimal.ZERO);
        empty.setNewMemberCount(0);
        return empty;
    }

    public Map<String, Object> getPriorSevenDayAverages(LocalDate today) {
        return jdbcTemplate.queryForMap(
                "SELECT IFNULL(AVG(sales_amount), 0) as avgSales, "
                        + "IFNULL(AVG(order_count), 0) as avgOrders, "
                        + "IFNULL(AVG(profit_amount), 0) as avgProfit, "
                        + "IFNULL(AVG(asp), 0) as avgAsp "
                        + "FROM oms_daily_summary WHERE record_date >= ? AND record_date < ?",
                today.minusDays(7), today);
    }
}
