package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceOperatingAnalysisQuery;
import com.money.contract.trade.FinanceOperatingMetricSnapshot;
import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.mapper.OmsOrderAnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** TRADE implementation preserving the established period-metric query formula. */
@Service
@RequiredArgsConstructor
class FinanceOperatingAnalysisQueryService implements FinanceOperatingAnalysisQuery {
    private final OmsOrderAnalysisMapper orderAnalysisMapper;

    @Override
    public List<FinanceOperatingMetricSnapshot> listPeriodMetrics(LocalDateTime startInclusive,
                                                                    LocalDateTime endInclusive,
                                                                    String periodDimension) {
        return orderAnalysisMapper.getPeriodAtomicStats(startInclusive, endInclusive, periodDimension).stream()
                .map(row -> new FinanceOperatingMetricSnapshot(row.getPeriod(), longValue(row.getOrderCount()),
                        longValue(row.getGoodsCount()), zero(row.getNetSalesAmount()), zero(row.getCostAmount())))
                .collect(Collectors.toList());
    }

    private long longValue(Integer value) { return value == null ? 0L : value.longValue(); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
