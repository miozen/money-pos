package com.money.dto.cloud;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Entity-free, read-only projection for a future Omnia Cloud member query service. */
@Data
public class MemberCloudSnapshot {
    private Long memberId;
    private Long tenantId;
    private String status;
    private Long mergedToMemberId;
    private String code;
    private String name;
    private String phone;
    private String type;
    private BigDecimal balance;
    private BigDecimal couponBalance;
    private LocalDateTime sourceUpdatedAt;
    private Map<String, String> brandLevels;
    private List<CouponWallet> unusedCoupons;
    private List<QuantityRight> quantityRights;
    private List<AmountRight> amountRights;
    private List<TargetPlan> targetPlans;

    @Data public static class CouponWallet { private Long ruleId; private String ruleName; private Long count; }
    @Data public static class QuantityRight { private Long rightId; private String brandId; private Long goodsId; private String goodsName; private Integer remainingQuantity; private String status; }
    @Data public static class AmountRight { private Long rightId; private String brandId; private String tierCode; private String tierName; private String pricingLevelCode; private BigDecimal remainingAmount; private String status; }
    @Data public static class TargetPlan { private Long planId; private String brandId; private String currentLevelCode; private String targetTierCode; private String targetTierName; private String targetPricingLevelCode; private BigDecimal targetAmount; private BigDecimal progressAmount; private BigDecimal remainingAmount; private String status; }
}
