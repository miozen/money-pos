package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned traffic aggregates used by FIN's hourly, weekly and monthly views. */
public interface FinanceTrafficQuery {
    List<FinanceHourlyTrafficSnapshot> listHourlyMetrics(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                          Integer mysqlDayOfWeek, Double divisor);
    List<FinanceTimeTrafficSnapshot> listWeeklyMetrics(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                        Double divisor);
    List<FinanceTimeTrafficSnapshot> listMonthlyMetrics(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                         Double divisor);
}
