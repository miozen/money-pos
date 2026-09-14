package com.money.feature.fin.application.analysis;

import cn.hutool.core.util.StrUtil;
import com.money.mapper.OmsOrderAuditMapper;
import com.money.feature.fin.application.analysis.FinanceRiskService;
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

    private final OmsOrderAuditMapper omsOrderAuditMapper;

    @Override
    public Map<String, Object> getRiskSummary(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        // 1. 获取收银员操作统计 (处理改价让利和退单数)
        List<Map<String, Object>> cashierRiskList = omsOrderAuditMapper.getCashierRiskSummary(startTime, endTime);

        // 2. 获取高危异常单据清单
        List<Map<String, Object>> recentAbnormalOrders = omsOrderAuditMapper.getAbnormalOrderList(startTime, endTime);

        // 3. 实时聚合前端卡片指标
        int abnormalOrderCount = recentAbnormalOrders.size();
        BigDecimal totalLossAmount = BigDecimal.ZERO;
        BigDecimal totalManualDiscount = BigDecimal.ZERO;
        long totalRefundCount = 0;

        // 计算损失总额（只加负毛利的部分）
        for (Map<String, Object> order : recentAbnormalOrders) {
            BigDecimal profit = new BigDecimal(order.get("profit").toString());
            if (profit.compareTo(BigDecimal.ZERO) < 0) {
                totalLossAmount = totalLossAmount.add(profit.abs());
            }
        }

        // 计算手工让利和退单总数
        for (Map<String, Object> cashier : cashierRiskList) {
            BigDecimal manual = new BigDecimal(cashier.get("manualDiscountAmount").toString());
            totalManualDiscount = totalManualDiscount.add(manual);
            totalRefundCount += ((Number) cashier.get("refundCount")).longValue();
        }

        // 4. 装配返回 DTO (严格对齐前端 data 结构)
        Map<String, Object> result = new HashMap<>();
        result.put("abnormalOrderCount", abnormalOrderCount);
        result.put("totalLossAmount", totalLossAmount);
        result.put("totalManualDiscount", totalManualDiscount);
        result.put("totalRefundCount", totalRefundCount);
        result.put("cashierRiskList", cashierRiskList);
        result.put("recentAbnormalOrders", recentAbnormalOrders);

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
}