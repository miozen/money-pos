package com.money.feature.trade.application.orderquery;

import com.money.contract.trade.FinanceOperatingAnalysisQuery;
import com.money.contract.trade.FinanceOperatingMetricSnapshot;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Preserves the legacy OMS statistics aggregate while keeping its read path in TRADE.
 */
@Service
@RequiredArgsConstructor
public class OmsOrderStatisticsQueryServiceImpl implements OmsOrderStatisticsQueryService {

    private final FinanceOperatingAnalysisQuery financeOperatingAnalysisQuery;

    @Override
    public OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime) {
        List<FinanceOperatingMetricSnapshot> stats = financeOperatingAnalysisQuery
                .listPeriodMetrics(startTime, endTime, "DAILY");
        OrderCountVO vo = new OrderCountVO();
        long totalOrder = 0;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (FinanceOperatingMetricSnapshot stat : stats) {
            totalOrder += stat.getOrderCount();
            totalSales = MoneyUtil.add(totalSales, stat.getNetSalesAmount());
            totalCost = MoneyUtil.add(totalCost, stat.getCostAmount());
        }

        vo.setOrderCount(totalOrder);
        vo.setTotalSales(totalSales);
        vo.setSaleCount(totalSales);
        vo.setCostCount(totalCost);
        vo.setProfit(MoneyUtil.subtract(totalSales, totalCost));
        return vo;
    }
}
