package com.money.feature.trade.application.report;

import com.money.contract.trade.FinanceAbnormalOrderSnapshot;
import com.money.contract.trade.FinanceCashierRiskSnapshot;
import com.money.contract.trade.FinanceRiskQuery;
import com.money.mapper.OmsOrderAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE implementation of the established FIN risk-control audit projections. */
@Service
@RequiredArgsConstructor
class FinanceRiskQueryService implements FinanceRiskQuery {

    private final OmsOrderAuditMapper orderAuditMapper;

    @Override
    public List<FinanceCashierRiskSnapshot> listCashierRiskSummaries(LocalDateTime startInclusive,
                                                                       LocalDateTime endInclusive) {
        return orderAuditMapper.getCashierRiskSummary(startInclusive, endInclusive).stream()
                .map(row -> new FinanceCashierRiskSnapshot(string(row.get("cashierName")), longValue(row.get("orderCount")),
                        amount(row.get("manualDiscountAmount")), longValue(row.get("refundCount"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<FinanceAbnormalOrderSnapshot> listAbnormalOrders(LocalDateTime startInclusive,
                                                                   LocalDateTime endInclusive) {
        return orderAuditMapper.getAbnormalOrderList(startInclusive, endInclusive).stream()
                .map(row -> new FinanceAbnormalOrderSnapshot(string(row.get("orderNo")), string(row.get("createTime")),
                        string(row.get("cashier")), amount(row.get("payAmount")), amount(row.get("costAmount")),
                        amount(row.get("profit")), string(row.get("riskType"))))
                .collect(Collectors.toList());
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private long longValue(Object value) { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
    private BigDecimal amount(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
