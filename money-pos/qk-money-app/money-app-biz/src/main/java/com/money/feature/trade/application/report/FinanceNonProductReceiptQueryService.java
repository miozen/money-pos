package com.money.feature.trade.application.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.contract.trade.FinanceNonProductReceiptDailySnapshot;
import com.money.contract.trade.FinanceNonProductReceiptPaymentSnapshot;
import com.money.contract.trade.FinanceNonProductReceiptQuery;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberAmountReceiptPay;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetReceiptPay;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberAmountReceiptPayMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetReceiptPayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates the two TRADE-owned non-product payment ledgers without exposing their persistence model. */
@Service
@RequiredArgsConstructor
class FinanceNonProductReceiptQueryService implements FinanceNonProductReceiptQuery {
    private final OmsMemberAmountReceiptPayMapper amountPayMapper;
    private final OmsMemberTargetReceiptPayMapper targetPayMapper;

    @Override
    public List<FinanceNonProductReceiptDailySnapshot> listDailySnapshots(LocalDate startInclusive, LocalDate endInclusive) {
        Map<LocalDate, Totals> totalsByDate = new TreeMap<>();
        merge(totalsByDate, amountPayMapper.selectMaps(paymentQuery(startInclusive, endInclusive, "net_amount")));
        merge(totalsByDate, targetPayMapper.selectMaps(paymentQuery(startInclusive, endInclusive, "pay_amount")));
        return totalsByDate.entrySet().stream()
                .map(entry -> new FinanceNonProductReceiptDailySnapshot(entry.getKey(), entry.getValue().income,
                        entry.getValue().collection, entry.getValue().refund))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<FinanceNonProductReceiptPaymentSnapshot> listDailyPaymentSummaries(LocalDate startInclusive, LocalDate endInclusive) {
        Map<PaymentKey, BigDecimal> totals = new TreeMap<>();
        mergePayments(totals, amountPayMapper.selectMaps(paymentSummaryQuery(startInclusive, endInclusive, "net_amount")));
        mergePayments(totals, targetPayMapper.selectMaps(paymentSummaryQuery(startInclusive, endInclusive, "pay_amount")));
        return totals.entrySet().stream()
                .map(entry -> new FinanceNonProductReceiptPaymentSnapshot(entry.getKey().date, entry.getKey().methodCode,
                        entry.getKey().payTag, entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
    }

    private <T> QueryWrapper<T> paymentQuery(LocalDate startInclusive, LocalDate endInclusive, String amountColumn) {
        QueryWrapper<T> query = new QueryWrapper<T>().select(
                "DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr",
                "SUM(CASE WHEN " + amountColumn + " > 0 THEN " + amountColumn + " ELSE 0 END) AS incomeAmount",
                "SUM(" + amountColumn + ") AS collectionAmount",
                "SUM(CASE WHEN " + amountColumn + " < 0 THEN -" + amountColumn + " ELSE 0 END) AS refundAmount")
                .groupBy("DATE(create_time)").orderByAsc("DATE(create_time)");
        if (startInclusive != null) query.ge("create_time", LocalDateTime.of(startInclusive, LocalTime.MIN));
        if (endInclusive != null) query.le("create_time", LocalDateTime.of(endInclusive, LocalTime.MAX));
        return query;
    }

    private <T> QueryWrapper<T> paymentSummaryQuery(LocalDate startInclusive, LocalDate endInclusive, String amountColumn) {
        QueryWrapper<T> query = new QueryWrapper<T>().select(
                "DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "pay_method_code AS methodCode",
                "pay_tag AS payTag", "SUM(" + amountColumn + ") AS netAmount")
                .groupBy("DATE(create_time)", "pay_method_code", "pay_tag").orderByAsc("DATE(create_time)");
        if (startInclusive != null) query.ge("create_time", LocalDateTime.of(startInclusive, LocalTime.MIN));
        if (endInclusive != null) query.le("create_time", LocalDateTime.of(endInclusive, LocalTime.MAX));
        return query;
    }

    private void merge(Map<LocalDate, Totals> totalsByDate, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            LocalDate date = LocalDate.parse(String.valueOf(row.get("dateStr")));
            Totals totals = totalsByDate.computeIfAbsent(date, ignored -> new Totals());
            totals.income = totals.income.add(amount(row.get("incomeAmount")));
            totals.collection = totals.collection.add(amount(row.get("collectionAmount")));
            totals.refund = totals.refund.add(amount(row.get("refundAmount")));
        }
    }

    private void mergePayments(Map<PaymentKey, BigDecimal> totals, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            PaymentKey key = new PaymentKey(LocalDate.parse(String.valueOf(row.get("dateStr"))), string(row.get("methodCode")),
                    string(row.get("payTag")));
            totals.merge(key, amount(row.get("netAmount")), BigDecimal::add);
        }
    }

    private BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }

    private static class Totals {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal collection = BigDecimal.ZERO;
        private BigDecimal refund = BigDecimal.ZERO;
    }

    private static class PaymentKey implements Comparable<PaymentKey> {
        private final LocalDate date;
        private final String methodCode;
        private final String payTag;

        private PaymentKey(LocalDate date, String methodCode, String payTag) {
            this.date = date;
            this.methodCode = methodCode;
            this.payTag = payTag;
        }

        @Override
        public int compareTo(PaymentKey other) {
            int dateOrder = date.compareTo(other.date);
            if (dateOrder != 0) return dateOrder;
            int methodOrder = nullSafe(methodCode).compareTo(nullSafe(other.methodCode));
            return methodOrder != 0 ? methodOrder : nullSafe(payTag).compareTo(nullSafe(other.payTag));
        }

        private static String nullSafe(String value) { return value == null ? "" : value; }
    }
}
