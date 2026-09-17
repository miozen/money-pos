package com.money.contract.member;

import java.math.BigDecimal;

/** Aggregate member principal and gift assets used only for FIN ratio presentation. */
public class FinanceMemberAssetCompositionSnapshot {

    private final BigDecimal principalAmount;
    private final BigDecimal giftAmount;

    public FinanceMemberAssetCompositionSnapshot(BigDecimal principalAmount, BigDecimal giftAmount) {
        this.principalAmount = principalAmount;
        this.giftAmount = giftAmount;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getGiftAmount() {
        return giftAmount;
    }
}
