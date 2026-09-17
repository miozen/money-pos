package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceHourlyTrafficSnapshot;
import com.money.contract.trade.FinanceTimeTrafficSnapshot;
import com.money.contract.trade.FinanceTrafficQuery;
import com.money.dto.OmsOrder.OmsSalesDataVO.HourlyTrafficVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.TimeTrafficVO;
import com.money.mapper.OmsOrderTrafficMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** TRADE implementation preserving the existing traffic SQL and database-side divisor calculation. */
@Service
@RequiredArgsConstructor
class FinanceTrafficQueryService implements FinanceTrafficQuery {
    private final OmsOrderTrafficMapper orderTrafficMapper;

    @Override
    public List<FinanceHourlyTrafficSnapshot> listHourlyMetrics(LocalDateTime startInclusive,
                                                                 LocalDateTime endInclusive,
                                                                 Integer mysqlDayOfWeek, Double divisor) {
        return orderTrafficMapper.getHourlyTrafficAnalysis(startInclusive, endInclusive, mysqlDayOfWeek, divisor).stream()
                .map(row -> new FinanceHourlyTrafficSnapshot(intValue(row.getHour()), zero(row.getAvgOrderCount()),
                        zero(row.getAvgSalesAmount()), longValue(row.getTotalOrderCount()), zero(row.getTotalSalesAmount())))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceTimeTrafficSnapshot> listWeeklyMetrics(LocalDateTime startInclusive,
                                                               LocalDateTime endInclusive, Double divisor) {
        return toTimeSnapshots(orderTrafficMapper.getWeeklyTrafficAnalysis(startInclusive, endInclusive, divisor));
    }

    @Override
    public List<FinanceTimeTrafficSnapshot> listMonthlyMetrics(LocalDateTime startInclusive,
                                                                LocalDateTime endInclusive, Double divisor) {
        return toTimeSnapshots(orderTrafficMapper.getMonthlyTrafficAnalysis(startInclusive, endInclusive, divisor));
    }

    private List<FinanceTimeTrafficSnapshot> toTimeSnapshots(List<TimeTrafficVO> rows) {
        return rows.stream().map(row -> new FinanceTimeTrafficSnapshot(intValue(row.getTimeKey()),
                zero(row.getAvgOrderCount()), zero(row.getAvgSalesAmount()), longValue(row.getTotalOrderCount()),
                zero(row.getTotalSalesAmount()))).collect(Collectors.toList());
    }

    private int intValue(Integer value) { return value == null ? 0 : value; }
    private long longValue(BigDecimal value) { return value == null ? 0L : value.longValue(); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
