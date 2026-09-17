package com.money.feature.trade.application.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.contract.trade.HomeDailyOrderSnapshot;
import com.money.contract.trade.HomeDashboardOrderSnapshot;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.contract.trade.HomeOrderReadSnapshot;
import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.entity.OmsOrder;
import com.money.mapper.OmsOrderAnalysisMapper;
import com.money.mapper.OmsOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** TRADE implementation of the established HOME count aggregate. */
@Service
@RequiredArgsConstructor
class HomeOrderReadQueryService implements HomeOrderReadQuery {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderAnalysisMapper omsOrderAnalysisMapper;

    @Override
    public HomeOrderReadSnapshot summarizeHomeCount(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        QueryWrapper<OmsOrder> wrapper = new QueryWrapper<>();
        wrapper.select(
                "COUNT(id) AS orderCount",
                "IFNULL(SUM(IFNULL(final_sales_amount, pay_amount)), 0) AS saleCount",
                "IFNULL(SUM(cost_amount), 0) AS costCount"
        );
        wrapper.in("status", OrderStatusEnum.getValidFinancialStatus());
        if (startInclusive != null) {
            wrapper.ge("create_time", startInclusive);
        }
        if (endExclusive != null) {
            wrapper.lt("create_time", endExclusive);
        }

        long orderCount = 0L;
        BigDecimal saleCount = BigDecimal.ZERO;
        BigDecimal costCount = BigDecimal.ZERO;
        List<Map<String, Object>> maps = omsOrderMapper.selectMaps(wrapper);
        if (maps != null && !maps.isEmpty() && maps.get(0) != null) {
            Map<String, Object> map = maps.get(0);
            orderCount = toLong(map.get("orderCount"));
            saleCount = toDecimal(map.get("saleCount"));
            costCount = toDecimal(map.get("costCount"));
        }
        return new HomeOrderReadSnapshot(orderCount, saleCount, costCount, saleCount.subtract(costCount));
    }

    @Override
    public HomeDailyOrderSnapshot summarizeDailySnapshot(LocalDate date) {
        List<AnalysisAtomicDataDTO> stats = omsOrderAnalysisMapper.getPeriodAtomicStats(
                date.atStartOfDay(), date.atTime(LocalTime.MAX), "DAILY");
        if (stats == null || stats.isEmpty()) {
            return new HomeDailyOrderSnapshot(0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        AnalysisAtomicDataDTO dayStat = stats.get(0);
        return new HomeDailyOrderSnapshot(
                dayStat.getOrderCount() == null ? 0 : dayStat.getOrderCount(),
                dayStat.getNetSalesAmount() == null ? BigDecimal.ZERO : dayStat.getNetSalesAmount(),
                dayStat.getCostAmount() == null ? BigDecimal.ZERO : dayStat.getCostAmount());
    }

    @Override
    public HomeDashboardOrderSnapshot summarizeDashboardRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        QueryWrapper<OmsOrder> wrapper = new QueryWrapper<>();
        wrapper.select(
                "COUNT(id) AS orderCount",
                "IFNULL(SUM(IFNULL(final_sales_amount, pay_amount)), 0) AS saleCount",
                "IFNULL(SUM(IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)), 0) AS profit"
        );
        wrapper.in("status", "PAID", "COMPLETED", "PARTIAL_REFUNDED");
        if (startInclusive != null) {
            wrapper.ge("create_time", startInclusive);
        }
        if (endExclusive != null) {
            wrapper.lt("create_time", endExclusive);
        }
        List<Map<String, Object>> maps = omsOrderMapper.selectMaps(wrapper);
        if (maps == null || maps.isEmpty() || maps.get(0) == null) {
            return new HomeDashboardOrderSnapshot(0L, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Map<String, Object> map = maps.get(0);
        return new HomeDashboardOrderSnapshot(
                toLong(map.get("orderCount")), toDecimal(map.get("saleCount")), toDecimal(map.get("profit")));
    }

    private long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private BigDecimal toDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
