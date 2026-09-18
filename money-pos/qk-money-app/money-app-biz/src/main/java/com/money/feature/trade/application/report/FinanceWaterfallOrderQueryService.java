package com.money.feature.trade.application.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.contract.trade.FinanceWaterfallOrderQuery;
import com.money.contract.trade.FinanceWaterfallOrderSnapshot;
import com.money.entity.OmsOrder;
import com.money.mapper.OmsOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE implementation of the established daily waterfall order aggregation. */
@Service
@RequiredArgsConstructor
class FinanceWaterfallOrderQueryService implements FinanceWaterfallOrderQuery {
    private final OmsOrderMapper orderMapper;

    @Override
    public List<FinanceWaterfallOrderSnapshot> listDailyWaterfallOrders(LocalDateTime startInclusive,
                                                                          LocalDateTime endInclusive) {
        QueryWrapper<OmsOrder> query = new QueryWrapper<OmsOrder>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr",
                        "SUM(IFNULL(total_amount, 0)) AS totalAmount",
                        "SUM(IFNULL(actual_coupon_deduct, 0)) AS couponAmount",
                        "SUM(IFNULL(use_voucher_amount, 0)) AS voucherAmount",
                        "SUM(IFNULL(manual_discount_amount, 0)) AS manualDiscountAmount",
                        "SUM(IFNULL(pay_amount, 0)) AS payAmount",
                        "SUM(pay_amount - IFNULL(final_sales_amount, pay_amount)) AS refundAmount",
                        "SUM(IFNULL(final_sales_amount, pay_amount)) AS netIncome")
                .in("status", OrderStatusEnum.getValidFinancialStatus())
                .groupBy("DATE(create_time)")
                .orderByDesc("DATE(create_time)");
        if (startInclusive != null) query.ge("create_time", startInclusive);
        if (endInclusive != null) query.le("create_time", endInclusive);
        return orderMapper.selectMaps(query).stream()
                .map(row -> new FinanceWaterfallOrderSnapshot(string(row.get("dateStr")), amount(row.get("totalAmount")),
                        amount(row.get("couponAmount")), amount(row.get("voucherAmount")),
                        amount(row.get("manualDiscountAmount")), amount(row.get("payAmount")),
                        amount(row.get("refundAmount")), amount(row.get("netIncome"))))
                .collect(Collectors.toList());
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private BigDecimal amount(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
