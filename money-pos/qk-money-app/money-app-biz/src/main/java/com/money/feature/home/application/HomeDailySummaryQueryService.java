package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
