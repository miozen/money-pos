package com.money.contract.member;

import java.math.BigDecimal;

/** One legacy RECHARGE or REVERSAL real-cash entry for FIN aggregation. */
public class FinanceMemberRechargeSnapshot {

    private final BigDecimal realAmount;

    public FinanceMemberRechargeSnapshot(BigDecimal realAmount) {
        this.realAmount = realAmount;
    }

    public BigDecimal getRealAmount() {
        return realAmount;
    }
}
