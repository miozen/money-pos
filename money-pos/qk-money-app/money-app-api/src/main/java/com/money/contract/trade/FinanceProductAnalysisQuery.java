package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned category-sales and selected-goods trend facts for FIN analysis. */
public interface FinanceProductAnalysisQuery {
    List<FinanceCategorySalesSnapshot> listCategorySales(LocalDateTime startInclusive, LocalDateTime endInclusive);
    List<FinanceDailyGoodsMetricSnapshot> listDailyGoodsMetrics(LocalDateTime startInclusive,
                                                                 LocalDateTime endInclusive, List<Long> goodsIds);
}
