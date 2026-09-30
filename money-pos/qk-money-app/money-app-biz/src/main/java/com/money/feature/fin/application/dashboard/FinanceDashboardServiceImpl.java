package com.money.feature.fin.application.dashboard;

import com.money.contract.goods.FinanceInventoryDocumentQuery;
import com.money.contract.goods.FinanceInventoryDocumentSnapshot;
import com.money.contract.member.FinanceMemberAssetQuery;
import com.money.contract.member.FinanceMemberRechargeSnapshot;
import com.money.contract.member.FinanceMemberRechargeTotalSnapshot;
import com.money.contract.trade.FinanceChannelDiscountSnapshot;
import com.money.contract.trade.FinanceDailyOrderMetricSnapshot;
import com.money.contract.trade.FinanceOrderPaymentQuery;
import com.money.contract.trade.FinanceNonProductReceiptDailySnapshot;
import com.money.contract.trade.FinanceNonProductReceiptPaymentSnapshot;
import com.money.contract.trade.FinanceNonProductReceiptQuery;
import com.money.contract.trade.FinancePaymentSummarySnapshot;
import com.money.contract.trade.FinanceRefundBaseSnapshot;
import com.money.contract.trade.FinanceTodayAssetOrderMetricsSnapshot;
import com.money.dto.Finance.FinanceDataVO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private final FinanceOrderPaymentQuery financeOrderPaymentQuery;
    private final FinanceNonProductReceiptQuery financeNonProductReceiptQuery;
    private final FinanceMemberAssetQuery financeMemberAssetQuery;
    private final FinanceInventoryDocumentQuery financeInventoryDocumentQuery;
    private final FinanceDashboardAssembler assembler; // 🌟 专职组装工厂

    @Override
    public AssetDashboardVO getAssetDashboard() {
        FinanceTodayAssetOrderMetricsSnapshot metrics = financeOrderPaymentQuery.getTodayAssetOrderMetrics(LocalDate.now());
        FinanceNonProductReceiptDailySnapshot receiptSnapshot = nonProductFor(LocalDate.now());
        AssetDashboardVO dashboard = new AssetDashboardVO();
        dashboard.setTodayRealCash(metrics.getFinalSalesAmount().add(receiptSnapshot.getCollectionAmount()));
        dashboard.setTodayWaivedAmount(metrics.getWaivedCouponAmount());
        dashboard.setTodayAssetDeduct(metrics.getActualCouponDeduct());

        // 委托装配器计算比例
        assembler.assembleAssetDashboard(dashboard, financeMemberAssetQuery.getAssetComposition());
        return dashboard;
    }

    @Override
    public FinanceDashboardVO getDashboardData(String date) {
        LocalDate targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        FinanceDashboardVO vo = new FinanceDashboardVO();

        // 1. 抓取原始数据
        List<FinanceDailyOrderMetricSnapshot> dailyOrders = financeOrderPaymentQuery.listDailyOrderMetrics(targetDate);

        List<FinanceInventoryDocumentSnapshot> inventoryDocs = financeInventoryDocumentQuery
                .listDailyFinancialDocuments(targetDate);

        List<FinancePaymentSummarySnapshot> dailyNetPays = financeOrderPaymentQuery
                .listDailyPaymentSummaries(targetDate, targetDate);
        List<FinanceNonProductReceiptDailySnapshot> dailyNonProduct = financeNonProductReceiptQuery
                .listDailySnapshots(targetDate, targetDate);
        dailyNetPays = mergePayments(dailyNetPays, financeNonProductReceiptQuery.listDailyPaymentSummaries(targetDate, targetDate));

        List<FinanceMemberRechargeSnapshot> dailyRecharges = financeMemberAssetQuery.listDailyRecharges(targetDate);
        BigDecimal totalDebt = financeMemberAssetQuery.getPositiveBalanceTotal();

        // 获取趋势所需基础数据
        List<FinancePaymentSummarySnapshot> paySummary = financeOrderPaymentQuery
                .listDailyPaymentSummaries(targetDate.minusDays(6), targetDate);
        List<FinanceNonProductReceiptDailySnapshot> nonProductSummary = financeNonProductReceiptQuery
                .listDailySnapshots(targetDate.minusDays(6), targetDate);
        paySummary = mergePayments(paySummary, financeNonProductReceiptQuery
                .listDailyPaymentSummaries(targetDate.minusDays(6), targetDate));
        List<FinanceMemberRechargeTotalSnapshot> rechargeSummary = financeMemberAssetQuery
                .listDailyRechargeTotals(targetDate.minusDays(6), targetDate);
        List<FinanceRefundBaseSnapshot> dailyOrderStats = financeOrderPaymentQuery
                .listDailyRefundBases(targetDate.minusDays(6), targetDate);

        // 2. 委托装配器组装结果
        assembler.assembleCoreMetrics(vo, dailyOrders, dailyNonProduct, inventoryDocs);
        assembler.assembleIncomeAndPie(vo, dailyNetPays, dailyRecharges, totalDebt);
        assembler.assembleTrendLines(vo, targetDate, paySummary, rechargeSummary, dailyOrderStats, nonProductSummary);

        return vo;
    }

    @Override
    public ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(6);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        // 1. 抓取原始数据
        List<FinancePaymentSummarySnapshot> paySummary = financeOrderPaymentQuery.listDailyPaymentSummaries(start, end);
        paySummary = mergePayments(paySummary, financeNonProductReceiptQuery.listDailyPaymentSummaries(start, end));
        List<FinanceChannelDiscountSnapshot> orderStats = financeOrderPaymentQuery.listDailyChannelDiscounts(start, end);

        // 2. 委托装配器组装结果
        ChannelMixAnalysisVO vo = new ChannelMixAnalysisVO();
        assembler.assembleChannelMix(vo, start, end, paySummary, orderStats);

        return vo;
    }

    private FinanceNonProductReceiptDailySnapshot nonProductFor(LocalDate date) {
        return financeNonProductReceiptQuery.listDailySnapshots(date, date).stream().findFirst()
                .orElse(new FinanceNonProductReceiptDailySnapshot(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private List<FinancePaymentSummarySnapshot> mergePayments(List<FinancePaymentSummarySnapshot> orderPayments,
                                                                List<FinanceNonProductReceiptPaymentSnapshot> receiptPayments) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, FinancePaymentSummarySnapshot> shapes = new LinkedHashMap<>();
        for (FinancePaymentSummarySnapshot payment : orderPayments) {
            mergePayment(totals, shapes, payment);
        }
        for (FinanceNonProductReceiptPaymentSnapshot payment : receiptPayments) {
            mergePayment(totals, shapes, new FinancePaymentSummarySnapshot(payment.getDate(), payment.getMethodCode(),
                    payment.getPayTag(), payment.getNetAmount()));
        }
        List<FinancePaymentSummarySnapshot> result = new ArrayList<>();
        for (Map.Entry<String, FinancePaymentSummarySnapshot> entry : shapes.entrySet()) {
            FinancePaymentSummarySnapshot shape = entry.getValue();
            result.add(new FinancePaymentSummarySnapshot(shape.getDate(), shape.getMethodCode(), shape.getPayTag(),
                    totals.get(entry.getKey())));
        }
        return result;
    }

    private void mergePayment(Map<String, BigDecimal> totals, Map<String, FinancePaymentSummarySnapshot> shapes,
                              FinancePaymentSummarySnapshot payment) {
        String key = payment.getDate() + "|" + payment.getMethodCode() + "|" + payment.getPayTag();
        shapes.putIfAbsent(key, payment);
        totals.merge(key, payment.getNetAmount(), BigDecimal::add);
    }
}
