package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceShiftBrandContributionSnapshot;
import com.money.contract.trade.FinanceShiftDiscountSnapshot;
import com.money.contract.trade.FinanceShiftHandoverQuery;
import com.money.contract.trade.FinanceShiftPaymentSnapshot;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE projection preserving the separate payment, discount and detail formulas of shift handover. */
@Service
@RequiredArgsConstructor
class FinanceShiftHandoverQueryService implements FinanceShiftHandoverQuery {
    private final OmsOrderMapper orderMapper;
    private final OmsOrderPayMapper orderPayMapper;
    private final OmsOrderDetailMapper orderDetailMapper;

    @Override
    public List<FinanceShiftPaymentSnapshot> listPaymentSummaries(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                                    String cashierName) {
        return orderPayMapper.getShiftPayStats(startInclusive, endInclusive, cashierName).stream()
                .map(row -> new FinanceShiftPaymentSnapshot(string(row.get("methodCode")), string(row.get("payTag")),
                        amount(row.get("netAmount"))))
                .collect(Collectors.toList());
    }

    @Override
    public FinanceShiftDiscountSnapshot getDiscountSummary(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                            String cashierName) {
        Map<String, Object> row = orderMapper.getShiftDiscountStats(startInclusive, endInclusive, cashierName);
        if (row == null) return new FinanceShiftDiscountSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0L, BigDecimal.ZERO);
        return new FinanceShiftDiscountSnapshot(amount(row.get("manualDiscount")), amount(row.get("voucherDiscount")),
                amount(row.get("memberCouponPay")), amount(row.get("waivedCouponAmount")),
                longValue(row.get("voucherCount")), amount(row.get("refundAmount")));
    }

    @Override
    public List<FinanceShiftBrandContributionSnapshot> listBrandContributions(LocalDateTime startInclusive,
                                                                                LocalDateTime endInclusive, String cashierName) {
        return orderDetailMapper.getShiftBrandContribution(startInclusive, endInclusive, cashierName).stream()
                .map(row -> new FinanceShiftBrandContributionSnapshot(string(row.get("brandId")), amount(row.get("revenue")),
                        amount(row.get("couponConsumption"))))
                .collect(Collectors.toList());
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private long longValue(Object value) { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
    private BigDecimal amount(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
