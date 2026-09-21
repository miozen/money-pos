package com.money.contract.member;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Entity-free command used by the legacy coupon-rule management endpoint. */
@Data
public class CouponRuleManagementCommand {
    private Long id;
    private String name;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private Integer status;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String tenantId;
}
