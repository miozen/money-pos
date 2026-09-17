package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceCategorySalesSnapshot;
import com.money.contract.trade.FinanceDailyGoodsMetricSnapshot;
import com.money.contract.trade.FinanceProductAnalysisQuery;
import com.money.mapper.OmsOrderAnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE implementation preserving independent category and daily-goods SQL formulas. */
@Service
@RequiredArgsConstructor
class FinanceProductAnalysisQueryService implements FinanceProductAnalysisQuery {
    private final OmsOrderAnalysisMapper orderAnalysisMapper;

    @Override
    public List<FinanceCategorySalesSnapshot> listCategorySales(LocalDateTime startInclusive, LocalDateTime endInclusive) {
        return orderAnalysisMapper.getCategorySalesAmounts(startInclusive, endInclusive).stream()
                .map(row -> new FinanceCategorySalesSnapshot(longObject(row.get("categoryId")),
                        longValue(row.get("salesQty")), decimal(row.get("salesAmount"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceDailyGoodsMetricSnapshot> listDailyGoodsMetrics(LocalDateTime startInclusive,
                                                                         LocalDateTime endInclusive,
                                                                         List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) return java.util.Collections.emptyList();
        return orderAnalysisMapper.getDailyGoodsStats(startInclusive, endInclusive, goodsIds).stream()
                .map(row -> new FinanceDailyGoodsMetricSnapshot(row.getDateStr(), row.getGoodsId(),
                        row.getGoodsName(), row.getSalesQty() == null ? 0L : row.getSalesQty().longValue()))
                .collect(Collectors.toList());
    }

    private Long longObject(Object value) { return value == null ? null : Long.valueOf(value.toString()); }
    private long longValue(Object value) { return value == null ? 0L : Long.parseLong(value.toString()); }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
}
