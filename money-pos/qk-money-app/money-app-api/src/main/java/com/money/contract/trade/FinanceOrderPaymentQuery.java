package com.money.contract.trade;

import java.time.LocalDate;
import java.util.List;

/** TRADE-owned financial dashboard inputs for FIN read-model assembly. */
public interface FinanceOrderPaymentQuery {

    List<FinanceDailyOrderMetricSnapshot> listDailyOrderMetrics(LocalDate date);

    List<FinancePaymentSummarySnapshot> listDailyPaymentSummaries(LocalDate startInclusive, LocalDate endInclusive);

    List<FinanceRefundBaseSnapshot> listDailyRefundBases(LocalDate startInclusive, LocalDate endInclusive);

    List<FinanceChannelDiscountSnapshot> listDailyChannelDiscounts(LocalDate startInclusive, LocalDate endInclusive);

    FinanceTodayAssetOrderMetricsSnapshot getTodayAssetOrderMetrics(LocalDate date);
}
