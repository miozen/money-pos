package com.money.feature.trade.application.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.contract.trade.FinanceChannelDiscountSnapshot;
import com.money.contract.trade.FinanceDailyOrderMetricSnapshot;
import com.money.contract.trade.FinanceOrderPaymentQuery;
import com.money.contract.trade.FinancePaymentSummarySnapshot;
import com.money.contract.trade.FinanceRefundBaseSnapshot;
import com.money.contract.trade.FinanceTodayAssetOrderMetricsSnapshot;
import com.money.entity.OmsOrder;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE-owned projection of the order/payment inputs used by the FIN dashboard. */
@Service
@RequiredArgsConstructor
class FinanceOrderPaymentQueryService implements FinanceOrderPaymentQuery {

    private final OmsOrderMapper orderMapper;
    private final OmsOrderPayMapper orderPayMapper;

    @Override
    public List<FinanceDailyOrderMetricSnapshot> listDailyOrderMetrics(LocalDate date) {
        return orderMapper.selectList(new LambdaQueryWrapper<OmsOrder>()
                        .select(OmsOrder::getTotalAmount, OmsOrder::getCouponAmount, OmsOrder::getActualCouponDeduct,
                                OmsOrder::getWaivedCouponAmount, OmsOrder::getUseVoucherAmount,
                                OmsOrder::getManualDiscountAmount, OmsOrder::getPayAmount,
                                OmsOrder::getFinalSalesAmount, OmsOrder::getCostAmount)
                        .ge(OmsOrder::getCreateTime, startOfDay(date))
                        .le(OmsOrder::getCreateTime, endOfDay(date))
                        .in(OmsOrder::getStatus, OrderStatusEnum.getValidFinancialStatus()))
                .stream()
                .map(order -> new FinanceDailyOrderMetricSnapshot(order.getTotalAmount(), order.getCouponAmount(),
                        order.getActualCouponDeduct(), order.getWaivedCouponAmount(), order.getUseVoucherAmount(),
                        order.getManualDiscountAmount(), order.getPayAmount(), order.getFinalSalesAmount(), order.getCostAmount()))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinancePaymentSummarySnapshot> listDailyPaymentSummaries(LocalDate startInclusive, LocalDate endInclusive) {
        return orderPayMapper.getDailyPaySummary(startOfDay(startInclusive), endOfDay(endInclusive)).stream()
                .map(row -> new FinancePaymentSummarySnapshot(date(row.get("dateStr")), string(row.get("methodCode")),
                        string(row.get("payTag")), amount(row.get("netAmount"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceRefundBaseSnapshot> listDailyRefundBases(LocalDate startInclusive, LocalDate endInclusive) {
        return orderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(pay_amount) AS payAmount",
                                "SUM(final_sales_amount) AS finalSalesAmount")
                        .ge("create_time", startOfDay(startInclusive)).le("create_time", endOfDay(endInclusive))
                        .in("status", OrderStatusEnum.getValidFinancialStatus()).groupBy("DATE(create_time)"))
                .stream()
                .map(row -> new FinanceRefundBaseSnapshot(date(row.get("dateStr")), amount(row.get("payAmount")),
                        amount(row.get("finalSalesAmount"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceChannelDiscountSnapshot> listDailyChannelDiscounts(LocalDate startInclusive, LocalDate endInclusive) {
        return orderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr",
                                "SUM(IFNULL(actual_coupon_deduct, 0)) AS actualCouponDeduct",
                                "SUM(IFNULL(use_voucher_amount, 0)) AS useVoucherAmount")
                        .ge("create_time", startOfDay(startInclusive)).le("create_time", endOfDay(endInclusive))
                        .in("status", OrderStatusEnum.getValidFinancialStatus()).groupBy("DATE(create_time)"))
                .stream()
                .map(row -> new FinanceChannelDiscountSnapshot(date(row.get("dateStr")),
                        amount(row.get("actualCouponDeduct")), amount(row.get("useVoucherAmount"))))
                .collect(Collectors.toList());
    }

    @Override
    public FinanceTodayAssetOrderMetricsSnapshot getTodayAssetOrderMetrics(LocalDate date) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                .select("IFNULL(SUM(final_sales_amount), 0) AS finalSalesAmount",
                        "IFNULL(SUM(waived_coupon_amount), 0) AS waivedCouponAmount",
                        "IFNULL(SUM(actual_coupon_deduct), 0) AS actualCouponDeduct")
                .ge("create_time", startOfDay(date)).le("create_time", endOfDay(date))
                .in("status", OrderStatusEnum.getValidFinancialStatus()));
        Map<String, Object> row = rows == null || rows.isEmpty() ? null : rows.get(0);
        return new FinanceTodayAssetOrderMetricsSnapshot(row == null ? BigDecimal.ZERO : amount(row.get("finalSalesAmount")),
                row == null ? BigDecimal.ZERO : amount(row.get("waivedCouponAmount")),
                row == null ? BigDecimal.ZERO : amount(row.get("actualCouponDeduct")));
    }

    private LocalDateTime startOfDay(LocalDate date) { return LocalDateTime.of(date, LocalTime.MIN); }
    private LocalDateTime endOfDay(LocalDate date) { return LocalDateTime.of(date, LocalTime.MAX); }
    private LocalDate date(Object value) { return LocalDate.parse(String.valueOf(value)); }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private BigDecimal amount(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
