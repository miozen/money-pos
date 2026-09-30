package com.money.contract.trade;

import java.time.LocalDate;
import java.util.List;

/** Entity-free FIN/HOME read boundary for member-benefit non-product receipt cash flows. */
public interface FinanceNonProductReceiptQuery {
    List<FinanceNonProductReceiptDailySnapshot> listDailySnapshots(LocalDate startInclusive, LocalDate endInclusive);

    List<FinanceNonProductReceiptPaymentSnapshot> listDailyPaymentSummaries(LocalDate startInclusive, LocalDate endInclusive);
}
