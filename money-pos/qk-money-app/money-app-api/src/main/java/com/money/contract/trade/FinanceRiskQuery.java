package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned audit inputs for the FIN risk-control report. */
public interface FinanceRiskQuery {

    List<FinanceCashierRiskSnapshot> listCashierRiskSummaries(LocalDateTime startInclusive,
                                                               LocalDateTime endInclusive);

    List<FinanceAbnormalOrderSnapshot> listAbnormalOrders(LocalDateTime startInclusive,
                                                           LocalDateTime endInclusive);
}
