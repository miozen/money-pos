package com.money.contract.member;

import java.math.BigDecimal;

/** POS-facing coupon-rule values without exposing the UMS persistence entity. */
public class PosCouponRuleSnapshot {

    private final Long id;
    private final String name;
    private final BigDecimal thresholdAmount;
    private final BigDecimal discountAmount;
    private final Integer status;
    private final int availableCount;

    public PosCouponRuleSnapshot(Long id, String name, BigDecimal thresholdAmount, BigDecimal discountAmount,
                                 Integer status, int availableCount) {
        this.id = id;
        this.name = name;
        this.thresholdAmount = thresholdAmount;
        this.discountAmount = discountAmount;
        this.status = status;
        this.availableCount = availableCount;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public Integer getStatus() { return status; }
    public int getAvailableCount() { return availableCount; }
}
