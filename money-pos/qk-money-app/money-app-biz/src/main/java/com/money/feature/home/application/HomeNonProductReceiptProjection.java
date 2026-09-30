package com.money.feature.home.application;

import com.money.contract.trade.FinanceNonProductReceiptDailySnapshot;
import com.money.contract.trade.FinanceNonProductReceiptQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** HOME adapter for TRADE's non-product receipt read contract; it never writes a receipt or daily summary. */
@Component
@RequiredArgsConstructor
class HomeNonProductReceiptProjection {
    private final FinanceNonProductReceiptQuery financeNonProductReceiptQuery;

    BigDecimal collectionFor(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        LocalDate start = startInclusive == null ? null : startInclusive.toLocalDate();
        LocalDate end = endExclusive == null ? null : endExclusive.toLocalDate().minusDays(1);
        if (start != null && end != null && end.isBefore(start)) return BigDecimal.ZERO;
        return financeNonProductReceiptQuery.listDailySnapshots(start, end).stream()
                .map(FinanceNonProductReceiptDailySnapshot::getCollectionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal collectionFor(LocalDate date) {
        return financeNonProductReceiptQuery.listDailySnapshots(date, date).stream()
                .map(FinanceNonProductReceiptDailySnapshot::getCollectionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<FinanceNonProductReceiptDailySnapshot> listFor(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        LocalDate start = startInclusive == null ? null : startInclusive.toLocalDate();
        LocalDate end = endExclusive == null ? null : endExclusive.toLocalDate().minusDays(1);
        return financeNonProductReceiptQuery.listDailySnapshots(start, end);
    }
}
