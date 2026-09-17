package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned order and payment inputs for the FIN shift-handover report. */
public interface FinanceShiftHandoverQuery {
    List<FinanceShiftPaymentSnapshot> listPaymentSummaries(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                            String cashierName);
    FinanceShiftDiscountSnapshot getDiscountSummary(LocalDateTime startInclusive, LocalDateTime endInclusive,
                                                    String cashierName);
    List<FinanceShiftBrandContributionSnapshot> listBrandContributions(LocalDateTime startInclusive,
                                                                        LocalDateTime endInclusive, String cashierName);
}
