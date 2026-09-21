package com.money.contract.member;

import java.math.BigDecimal;

/** A member-owned count paired with the UMS coupon-rule values needed by POS. */
public class MemberCouponRuleSnapshot {
    private final Long ruleId;
    private final String name;
    private final BigDecimal thresholdAmount;
    private final BigDecimal discountAmount;
    private final Long ownedCount;

    public MemberCouponRuleSnapshot(Long ruleId, String name, BigDecimal thresholdAmount,
                                    BigDecimal discountAmount, Long ownedCount) {
        this.ruleId = ruleId;
        this.name = name;
        this.thresholdAmount = thresholdAmount;
        this.discountAmount = discountAmount;
        this.ownedCount = ownedCount;
    }

    public Long getRuleId() { return ruleId; }
    public String getName() { return name; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public Long getOwnedCount() { return ownedCount; }
}
