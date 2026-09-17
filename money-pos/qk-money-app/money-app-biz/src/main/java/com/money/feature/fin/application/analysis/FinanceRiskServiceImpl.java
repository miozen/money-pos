package com.money.feature.fin.application.analysis;

import cn.hutool.core.util.StrUtil;
import com.money.contract.trade.FinanceAbnormalOrderSnapshot;
import com.money.contract.trade.FinanceCashierRiskSnapshot;
import com.money.contract.trade.FinanceRiskQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 门店收银防损风控大脑
 * 职责：调用审计集市数据，聚合生成风控雷达指标
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceRiskServiceImpl implements FinanceRiskService {

    private final FinanceRiskQuery financeRiskQuery;

    @Override
    public Map<String, Object> getRiskSummary(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        // 1. 获取收银员操作统计 (处理改价让利和退单数)
        List<FinanceCashierRiskSnapshot> cashierRiskSnapshots = financeRiskQuery
                .listCashierRiskSummaries(startTime, endTime);

        // 2. 获取高危异常单据清单
        List<FinanceAbnormalOrderSnapshot> abnormalOrderSnapshots = financeRiskQuery
                .listAbnormalOrders(startTime, endTime);

        // 3. 实时聚合前端卡片指标
        int abnormalOrderCount = abnormalOrderSnapshots.size();
        BigDecimal totalLossAmount = BigDecimal.ZERO;
        BigDecimal totalManualDiscount = BigDecimal.ZERO;
        long totalRefundCount = 0;

        // 计算损失总额（只加负毛利的部分）
        for (FinanceAbnormalOrderSnapshot order : abnormalOrderSnapshots) {
            BigDecimal profit = order.getProfitAmount() == null ? BigDecimal.ZERO : order.getProfitAmount();
            if (profit.compareTo(BigDecimal.ZERO) < 0) {
                totalLossAmount = totalLossAmount.add(profit.abs());
            }
        }

        // 计算手工让利和退单总数
        for (FinanceCashierRiskSnapshot cashier : cashierRiskSnapshots) {
            BigDecimal manual = cashier.getManualDiscountAmount() == null ? BigDecimal.ZERO : cashier.getManualDiscountAmount();
            totalManualDiscount = totalManualDiscount.add(manual);
            totalRefundCount += cashier.getRefundCount();
        }

        // 4. 装配返回 DTO (严格对齐前端 data 结构)
        Map<String, Object> result = new HashMap<>();
        result.put("abnormalOrderCount", abnormalOrderCount);
        result.put("totalLossAmount", totalLossAmount);
        result.put("totalManualDiscount", totalManualDiscount);
        result.put("totalRefundCount", totalRefundCount);
        result.put("cashierRiskList", cashierRiskSnapshots.stream()
                .map(this::toCashierRiskMap).collect(java.util.stream.Collectors.toList()));
        result.put("recentAbnormalOrders", abnormalOrderSnapshots.stream()
                .map(this::toAbnormalOrderMap).collect(java.util.stream.Collectors.toList()));

        return result;
    }

    private LocalDateTime parseStartTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return LocalDate.now().minusDays(6).atStartOfDay();
        return LocalDate.parse(dateStr).atStartOfDay();
    }

    private LocalDateTime parseEndTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return LocalDate.now().atTime(LocalTime.MAX);
        return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
    }

    private Map<String, Object> toCashierRiskMap(FinanceCashierRiskSnapshot snapshot) {
        Map<String, Object> row = new HashMap<>();
        row.put("cashierName", snapshot.getCashierName());
        row.put("orderCount", snapshot.getOrderCount());
        row.put("manualDiscountAmount", snapshot.getManualDiscountAmount());
        row.put("refundCount", snapshot.getRefundCount());
        return row;
    }

    private Map<String, Object> toAbnormalOrderMap(FinanceAbnormalOrderSnapshot snapshot) {
        Map<String, Object> row = new HashMap<>();
        row.put("orderNo", snapshot.getOrderNo());
        row.put("createTime", snapshot.getCreateTimeLabel());
        row.put("cashier", snapshot.getCashierName());
        row.put("payAmount", snapshot.getPayAmount());
        row.put("costAmount", snapshot.getCostAmount());
        row.put("profit", snapshot.getProfitAmount());
        row.put("riskType", snapshot.getRiskType());
        return row;
    }
}
