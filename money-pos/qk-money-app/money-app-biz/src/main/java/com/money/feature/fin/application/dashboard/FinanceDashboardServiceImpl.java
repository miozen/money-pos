package com.money.feature.fin.application.dashboard;

import com.money.contract.goods.FinanceInventoryDocumentQuery;
import com.money.contract.goods.FinanceInventoryDocumentSnapshot;
import com.money.contract.member.FinanceMemberAssetQuery;
import com.money.contract.member.FinanceMemberRechargeSnapshot;
import com.money.contract.member.FinanceMemberRechargeTotalSnapshot;
import com.money.contract.trade.FinanceChannelDiscountSnapshot;
import com.money.contract.trade.FinanceDailyOrderMetricSnapshot;
import com.money.contract.trade.FinanceOrderPaymentQuery;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private final FinanceOrderPaymentQuery financeOrderPaymentQuery;
    private final FinanceMemberAssetQuery financeMemberAssetQuery;
    private final FinanceInventoryDocumentQuery financeInventoryDocumentQuery;
    private final FinanceDashboardAssembler assembler; // 🌟 专职组装工厂

    @Override
    public AssetDashboardVO getAssetDashboard() {
        FinanceTodayAssetOrderMetricsSnapshot metrics = financeOrderPaymentQuery.getTodayAssetOrderMetrics(LocalDate.now());
        AssetDashboardVO dashboard = new AssetDashboardVO();
        dashboard.setTodayRealCash(metrics.getFinalSalesAmount());
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

        List<FinanceMemberRechargeSnapshot> dailyRecharges = financeMemberAssetQuery.listDailyRecharges(targetDate);
        BigDecimal totalDebt = financeMemberAssetQuery.getPositiveBalanceTotal();

        // 获取趋势所需基础数据
        List<FinancePaymentSummarySnapshot> paySummary = financeOrderPaymentQuery
                .listDailyPaymentSummaries(targetDate.minusDays(6), targetDate);
        List<FinanceMemberRechargeTotalSnapshot> rechargeSummary = financeMemberAssetQuery
                .listDailyRechargeTotals(targetDate.minusDays(6), targetDate);
        List<FinanceRefundBaseSnapshot> dailyOrderStats = financeOrderPaymentQuery
                .listDailyRefundBases(targetDate.minusDays(6), targetDate);

        // 2. 委托装配器组装结果
        assembler.assembleCoreMetrics(vo, dailyOrders, inventoryDocs);
        assembler.assembleIncomeAndPie(vo, dailyNetPays, dailyRecharges, totalDebt);
        assembler.assembleTrendLines(vo, targetDate, paySummary, rechargeSummary, dailyOrderStats);

        return vo;
    }

    @Override
    public ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(6);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        // 1. 抓取原始数据
        List<FinancePaymentSummarySnapshot> paySummary = financeOrderPaymentQuery.listDailyPaymentSummaries(start, end);
        List<FinanceChannelDiscountSnapshot> orderStats = financeOrderPaymentQuery.listDailyChannelDiscounts(start, end);

        // 2. 委托装配器组装结果
        ChannelMixAnalysisVO vo = new ChannelMixAnalysisVO();
        assembler.assembleChannelMix(vo, start, end, paySummary, orderStats);

        return vo;
    }
}
