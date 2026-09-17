package com.money.contract.member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only member-asset financial inputs owned by UMS. */
public interface FinanceMemberAssetQuery {

    List<FinanceMemberRechargeSnapshot> listDailyRecharges(LocalDate date);

    List<FinanceMemberRechargeTotalSnapshot> listDailyRechargeTotals(LocalDate startInclusive, LocalDate endInclusive);

    BigDecimal getPositiveBalanceTotal();

    FinanceMemberAssetCompositionSnapshot getAssetComposition();
}
