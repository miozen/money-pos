package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned period metrics used by FIN performance reports and summary cards. */
public interface FinanceOperatingAnalysisQuery {
    List<FinanceOperatingMetricSnapshot> listPeriodMetrics(LocalDateTime startInclusive,
                                                            LocalDateTime endInclusive,
                                                            String periodDimension);
}
