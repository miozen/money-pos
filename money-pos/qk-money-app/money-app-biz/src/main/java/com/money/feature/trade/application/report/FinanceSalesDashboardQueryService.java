package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceDashboardBrandSalesSnapshot;
import com.money.contract.trade.FinanceDashboardMemberDailySnapshot;
import com.money.contract.trade.FinanceDashboardTopGoodsSnapshot;
import com.money.contract.trade.FinanceSalesDashboardQuery;
import com.money.mapper.OmsOrderAnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE implementation of the sales-dashboard facts; GMS display names remain outside TRADE. */
@Service
@RequiredArgsConstructor
class FinanceSalesDashboardQueryService implements FinanceSalesDashboardQuery {
    private final OmsOrderAnalysisMapper orderAnalysisMapper;

    @Override
    public List<FinanceDashboardTopGoodsSnapshot> listTopGoods(LocalDateTime startInclusive, LocalDateTime endInclusive) {
        return orderAnalysisMapper.getTopGoodsRank(startInclusive, endInclusive).stream()
                .map(row -> new FinanceDashboardTopGoodsSnapshot(row.getGoodsId(), row.getGoodsName(),
                        longValue(row.getSalesQty()), zero(row.getSalesAmount())))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceDashboardBrandSalesSnapshot> listBrandSales(LocalDateTime startInclusive,
                                                                     LocalDateTime endInclusive) {
        return orderAnalysisMapper.getBrandSalesAmounts(startInclusive, endInclusive).stream()
                .map(row -> new FinanceDashboardBrandSalesSnapshot(longObject(row.get("brandId")),
                        decimal(row.get("salesAmount"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceDashboardMemberDailySnapshot> listDailyMemberMetrics(LocalDateTime startInclusive,
                                                                               LocalDateTime endInclusive) {
        return orderAnalysisMapper.getDailyMemberStats(startInclusive, endInclusive).stream()
                .map(row -> new FinanceDashboardMemberDailySnapshot(row.getDateStr(),
                        row.getIsMember() != null && row.getIsMember() == 1,
                        longValue(row.getOrderCount()), zero(row.getSalesAmount())))
                .collect(Collectors.toList());
    }

    private long longValue(Integer value) { return value == null ? 0L : value.longValue(); }
    private Long longObject(Object value) { return value == null ? null : Long.valueOf(value.toString()); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
}
