package com.money.feature.fin.application.dashboard;

import com.money.constant.PayMethodEnum;
import com.money.contract.goods.FinanceInventoryDocumentSnapshot;
import com.money.contract.member.FinanceMemberAssetCompositionSnapshot;
import com.money.contract.member.FinanceMemberRechargeSnapshot;
import com.money.contract.member.FinanceMemberRechargeTotalSnapshot;
import com.money.contract.trade.FinanceChannelDiscountSnapshot;
import com.money.contract.trade.FinanceDailyOrderMetricSnapshot;
import com.money.contract.trade.FinancePaymentSummarySnapshot;
import com.money.contract.trade.FinanceRefundBaseSnapshot;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.web.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🌟 财务大盘数据装配器：专职负责财务流水的聚合、核算与图表化
 */
@Slf4j
@Component
public class FinanceDashboardAssembler {

    private static final DateTimeFormatter FORMATTER_MM_DD = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter FORMATTER_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public BigDecimal null2Zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public BigDecimal parseAmt(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (Exception e) {
            log.error("💥 核心财务报表数据解析异常! 出现疑似脏数据的值: [{}]", val, e);
            throw new BaseException("财务数据严重异常：发现非法金额格式 [" + val + "]，为防止账目错乱，报表已熔断！");
        }
    }

    /**
     * 1. 组装今日资产概览 (本金/赠金比例)
     */
    public void assembleAssetDashboard(AssetDashboardVO dashboard, FinanceMemberAssetCompositionSnapshot composition) {
        if (composition != null) {
            BigDecimal principal = null2Zero(composition.getPrincipalAmount());
            BigDecimal gift = null2Zero(composition.getGiftAmount());
            BigDecimal total = principal.add(gift);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                dashboard.setPrincipalRatio(principal.multiply(new BigDecimal(100)).divide(total, 2, RoundingMode.HALF_UP));
                dashboard.setGiftRatio(gift.multiply(new BigDecimal(100)).divide(total, 2, RoundingMode.HALF_UP));
            } else {
                dashboard.setPrincipalRatio(BigDecimal.ZERO);
                dashboard.setGiftRatio(BigDecimal.ZERO);
            }
        } else {
            dashboard.setPrincipalRatio(BigDecimal.ZERO);
            dashboard.setGiftRatio(BigDecimal.ZERO);
        }
    }

    /**
     * 2. 组装核心交易指标 (应收/实收/退款/净收/毛利)
     */
    public void assembleCoreMetrics(FinanceDashboardVO vo, List<FinanceDailyOrderMetricSnapshot> dailyOrders,
                                    List<FinanceInventoryDocumentSnapshot> inventoryDocs) {
        BigDecimal totalAmount = BigDecimal.ZERO, totalDiscount = BigDecimal.ZERO;
        BigDecimal payAmount = BigDecimal.ZERO, refundAmount = BigDecimal.ZERO, costAmount = BigDecimal.ZERO;

        BigDecimal actualCouponDeduct = BigDecimal.ZERO, waivedCouponAmount = BigDecimal.ZERO;
        BigDecimal voucherAmount = BigDecimal.ZERO, manualDiscountAmount = BigDecimal.ZERO;

        for (FinanceDailyOrderMetricSnapshot o : dailyOrders) {
            totalAmount = totalAmount.add(null2Zero(o.getTotalAmount()));

            BigDecimal currentActualCoupon = o.getActualCouponDeduct() != null ? o.getActualCouponDeduct() : null2Zero(o.getCouponAmount());
            BigDecimal currentWaived = null2Zero(o.getWaivedCouponAmount());
            BigDecimal currentVoucher = null2Zero(o.getUseVoucherAmount());
            BigDecimal currentManual = null2Zero(o.getManualDiscountAmount());

            actualCouponDeduct = actualCouponDeduct.add(currentActualCoupon);
            waivedCouponAmount = waivedCouponAmount.add(currentWaived);
            voucherAmount = voucherAmount.add(currentVoucher);
            manualDiscountAmount = manualDiscountAmount.add(currentManual);

            totalDiscount = totalDiscount.add(currentActualCoupon).add(currentWaived).add(currentVoucher).add(currentManual);

            BigDecimal currentPay = null2Zero(o.getPayAmount());
            payAmount = payAmount.add(currentPay);
            refundAmount = refundAmount.add(currentPay.subtract(null2Zero(o.getFinalSalesAmount())));
            costAmount = costAmount.add(null2Zero(o.getCostAmount()));
        }

        BigDecimal netIncome = payAmount.subtract(refundAmount);
        BigDecimal salesGrossProfit = netIncome.subtract(costAmount);

        BigDecimal inventoryLoss = BigDecimal.ZERO;
        for (FinanceInventoryDocumentSnapshot doc : inventoryDocs) {
            if (doc.getTotalAmount() != null && doc.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
                inventoryLoss = inventoryLoss.add(doc.getTotalAmount().abs());
            }
        }

        vo.setTotalAmount(totalAmount);
        vo.setTotalDiscount(totalDiscount);
        vo.setActualCouponDeduct(actualCouponDeduct);
        vo.setWaivedCouponAmount(waivedCouponAmount);
        vo.setVoucherAmount(voucherAmount);
        vo.setManualDiscountAmount(manualDiscountAmount);
        vo.setPayAmount(payAmount);
        vo.setRefundAmount(refundAmount);
        vo.setNetIncome(netIncome);
        vo.setGrossProfit(salesGrossProfit.subtract(inventoryLoss));
    }

    /**
     * 3. 组装资金流入分布与饼图
     */
    public void assembleIncomeAndPie(FinanceDashboardVO vo, List<FinancePaymentSummarySnapshot> dailyNetPays,
                                     List<FinanceMemberRechargeSnapshot> dailyRecharges, BigDecimal totalDebt) {
        BigDecimal scanIncomeTotal = BigDecimal.ZERO, cashIncome = BigDecimal.ZERO, balancePay = BigDecimal.ZERO;
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (FinancePaymentSummarySnapshot payMap : dailyNetPays) {
            BigDecimal amt = null2Zero(payMap.getNetAmount());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;

            PayMethodEnum method = PayMethodEnum.fromCode(payMap.getMethodCode());
            if (method == null) method = PayMethodEnum.AGGREGATE;

            if (method == PayMethodEnum.BALANCE) balancePay = balancePay.add(amt);
            else if (method == PayMethodEnum.CASH) cashIncome = cashIncome.add(amt);
            else {
                scanIncomeTotal = scanIncomeTotal.add(amt);
                String rawTag = payMap.getPayTag();
                rawTag = (rawTag != null && !rawTag.trim().isEmpty()) ? rawTag : "UNKNOWN";
                scanTagMap.put(rawTag, scanTagMap.getOrDefault(rawTag, BigDecimal.ZERO).add(amt));
            }
        }

        BigDecimal rechargeAmount = dailyRecharges.stream().map(log -> null2Zero(log.getRealAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setExternalIncome(scanIncomeTotal.add(cashIncome).add(rechargeAmount));
        vo.setTotalDebt(totalDebt);

        List<PayPieData> pieDataList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("TAG:" + entry.getKey(), entry.getValue()));
        }
        if (scanTagMap.isEmpty() && scanIncomeTotal.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("聚合扫码流水", scanIncomeTotal));
        if (cashIncome.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银流水", cashIncome));
        if (balancePay.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("会员余额抵扣", balancePay));
        if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("充值/买券净收款", rechargeAmount));

        vo.setPayBreakdown(pieDataList);
    }

    /**
     * 4. 组装近7日财务趋势折线图
     */
    public void assembleTrendLines(FinanceDashboardVO vo, LocalDate targetDate, List<FinancePaymentSummarySnapshot> paySummary,
                                   List<FinanceMemberRechargeTotalSnapshot> rechargeSummary,
                                   List<FinanceRefundBaseSnapshot> dailyOrderStats) {
        Map<String, BigDecimal> historyRefundMap = new HashMap<>();
        for (FinanceRefundBaseSnapshot stat : dailyOrderStats) {
            String dStr = stat.getDate().format(FORMATTER_YYYY_MM_DD);
            BigDecimal dRefund = null2Zero(stat.getPayAmount()).subtract(null2Zero(stat.getFinalSalesAmount()));
            historyRefundMap.put(dStr, dRefund.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : dRefund);
        }

        Set<String> allTags = paySummary.stream().filter(r -> {
            PayMethodEnum m = PayMethodEnum.fromCode(r.getMethodCode());
            return m != PayMethodEnum.CASH && m != PayMethodEnum.BALANCE;
        }).map(r -> {
            String tag = r.getPayTag();
            return (tag != null && !tag.trim().isEmpty()) ? tag : "UNKNOWN";
        }).collect(Collectors.toSet());

        Map<String, List<BigDecimal>> dynamicTrendMap = new HashMap<>();
        for (String tag : allTags) dynamicTrendMap.put(tag, new ArrayList<>());

        List<String> trendDates = new ArrayList<>();
        List<BigDecimal> trendScan = new ArrayList<>(), trendCash = new ArrayList<>(), trendRecharge = new ArrayList<>(), trendTotal = new ArrayList<>(), trendRefund = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate d = targetDate.minusDays(i);
            String matchDateStr = d.format(FORMATTER_YYYY_MM_DD);
            trendDates.add(d.format(FORMATTER_MM_DD));
            trendRefund.add(historyRefundMap.getOrDefault(matchDateStr, BigDecimal.ZERO));

            BigDecimal dailyScan = BigDecimal.ZERO, dailyCash = BigDecimal.ZERO;
            Map<String, BigDecimal> dailyTagAmt = new HashMap<>();

            for(FinancePaymentSummarySnapshot r : paySummary){
                if(matchDateStr.equals(r.getDate().format(FORMATTER_YYYY_MM_DD))){
                    BigDecimal amt = null2Zero(r.getNetAmount());
                    PayMethodEnum method = PayMethodEnum.fromCode(r.getMethodCode());
                    if (method == PayMethodEnum.CASH) dailyCash = dailyCash.add(amt);
                    else if (method != PayMethodEnum.BALANCE) {
                        dailyScan = dailyScan.add(amt);
                        String tag = r.getPayTag();
                        tag = (tag != null && !tag.trim().isEmpty()) ? tag : "UNKNOWN";
                        dailyTagAmt.put(tag, dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO).add(amt));
                    }
                }
            }

            for (String tag : allTags) dynamicTrendMap.get(tag).add(dailyTagAmt.getOrDefault(tag, BigDecimal.ZERO));

            BigDecimal dailyRecharge = BigDecimal.ZERO;
            for(FinanceMemberRechargeTotalSnapshot r : rechargeSummary){
                if(d.equals(r.getDate())) dailyRecharge = dailyRecharge.add(null2Zero(r.getTotalAmount()));
            }

            trendScan.add(dailyScan); trendCash.add(dailyCash); trendRecharge.add(dailyRecharge);
            trendTotal.add(dailyScan.add(dailyCash).add(dailyRecharge));
        }

        vo.setTrendDates(trendDates); vo.setTrendScan(trendScan); vo.setTrendCash(trendCash);
        vo.setTrendRecharge(trendRecharge); vo.setTrendTotal(trendTotal); vo.setTrendRefund(trendRefund);
        vo.setDynamicTrendMap(dynamicTrendMap);
    }

    /**
     * 5. 组装渠道组合分析 (瀑布流视图)
     */
    public void assembleChannelMix(ChannelMixAnalysisVO vo, LocalDate start, LocalDate end, List<FinancePaymentSummarySnapshot> paySummary, List<FinanceChannelDiscountSnapshot> orderStats) {
        List<String> trendDates = new ArrayList<>();
        LocalDate temp = start;
        while (!temp.isAfter(end)) {
            trendDates.add(temp.format(FORMATTER_YYYY_MM_DD));
            temp = temp.plusDays(1);
        }
        vo.setTrendDates(trendDates);

        List<BigDecimal> scanList = new ArrayList<>(), cashList = new ArrayList<>(), balanceList = new ArrayList<>(), couponList = new ArrayList<>(), voucherList = new ArrayList<>();
        BigDecimal totalCash = BigDecimal.ZERO, totalBalance = BigDecimal.ZERO, totalCoupon = BigDecimal.ZERO, totalVoucher = BigDecimal.ZERO;
        Map<String, BigDecimal> scanTagMap = new HashMap<>();

        for (String dateStr : trendDates) {
            BigDecimal dailyScan = BigDecimal.ZERO, dailyCash = BigDecimal.ZERO, dailyBalance = BigDecimal.ZERO;

            for (FinancePaymentSummarySnapshot pay : paySummary) {
                if (dateStr.equals(pay.getDate().format(FORMATTER_YYYY_MM_DD))) {
                    BigDecimal amt = null2Zero(pay.getNetAmount());
                    PayMethodEnum method = PayMethodEnum.fromCode(pay.getMethodCode());
                    if (method == PayMethodEnum.CASH) {
                        dailyCash = dailyCash.add(amt); totalCash = totalCash.add(amt);
                    } else if (method == PayMethodEnum.BALANCE) {
                        dailyBalance = dailyBalance.add(amt); totalBalance = totalBalance.add(amt);
                    } else {
                        dailyScan = dailyScan.add(amt);
                        String tag = pay.getPayTag();
                        tag = (tag != null && !tag.trim().isEmpty()) ? "TAG:" + tag : "TAG:UNKNOWN";
                        scanTagMap.put(tag, scanTagMap.getOrDefault(tag, BigDecimal.ZERO).add(amt));
                    }
                }
            }
            scanList.add(dailyScan); cashList.add(dailyCash); balanceList.add(dailyBalance);

            BigDecimal dailyCoupon = BigDecimal.ZERO, dailyVoucher = BigDecimal.ZERO;
            for (FinanceChannelDiscountSnapshot stat : orderStats) {
                if (dateStr.equals(stat.getDate().format(FORMATTER_YYYY_MM_DD))) {
                    dailyCoupon = null2Zero(stat.getActualCouponDeduct());
                    dailyVoucher = null2Zero(stat.getUseVoucherAmount());
                }
            }
            couponList.add(dailyCoupon); totalCoupon = totalCoupon.add(dailyCoupon);
            voucherList.add(dailyVoucher); totalVoucher = totalVoucher.add(dailyVoucher);
        }

        vo.setScanList(scanList); vo.setCashList(cashList); vo.setBalanceList(balanceList);
        vo.setCouponList(couponList); vo.setVoucherList(voucherList);

        List<PayPieData> pieDataList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scanTagMap.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData(entry.getKey(), entry.getValue()));
        }
        if (totalCash.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("现金收银(真金)", totalCash));
        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("余额消耗(预收)", totalBalance));
        if (totalCoupon.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("单品会员券(让利)", totalCoupon));
        if (totalVoucher.compareTo(BigDecimal.ZERO) > 0) pieDataList.add(new PayPieData("整单满减券(让利)", totalVoucher));

        vo.setPieData(pieDataList);
    }
}
