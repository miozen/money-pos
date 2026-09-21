package com.money.contract.member;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** JSON-compatible coupon-rule response without exposing UMS persistence. */
public class CouponRuleManagementSnapshot {
    private final Long id;
    private final String name;
    private final BigDecimal thresholdAmount;
    private final BigDecimal discountAmount;
    private final Integer status;
    private final String createBy;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;
    private final String tenantId;

    public CouponRuleManagementSnapshot(Long id, String name, BigDecimal thresholdAmount, BigDecimal discountAmount,
                                        Integer status, String createBy, LocalDateTime createTime,
                                        LocalDateTime updateTime, String tenantId) {
        this.id = id;
        this.name = name;
        this.thresholdAmount = thresholdAmount;
        this.discountAmount = discountAmount;
        this.status = status;
        this.createBy = createBy;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.tenantId = tenantId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public Integer getStatus() { return status; }
    public String getCreateBy() { return createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public String getTenantId() { return tenantId; }
}
