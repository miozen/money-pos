package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned non-period inputs for FIN's sales dashboard. */
public interface FinanceSalesDashboardQuery {
    List<FinanceDashboardTopGoodsSnapshot> listTopGoods(LocalDateTime startInclusive, LocalDateTime endInclusive);
    List<FinanceDashboardBrandSalesSnapshot> listBrandSales(LocalDateTime startInclusive, LocalDateTime endInclusive);
    List<FinanceDashboardMemberDailySnapshot> listDailyMemberMetrics(LocalDateTime startInclusive,
                                                                       LocalDateTime endInclusive);
}
