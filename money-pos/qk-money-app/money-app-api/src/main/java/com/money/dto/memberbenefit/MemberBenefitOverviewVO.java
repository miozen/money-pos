package com.money.dto.memberbenefit;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only UMS view used by the member-benefit page; it contains no persistence entities. */
@Data
public class MemberBenefitOverviewVO {
    private List<Tier> tiers;
    private List<QuantityRight> quantityRights;
    private List<AmountRight> amountRights;
    private List<TargetPlan> targetPlans;

    @Data public static class Tier {
        private String brandId; private String tierCode; private String tierName; private BigDecimal configuredAmount;
        private String pricingLevelCode; private Integer rankValue; private Boolean enabled; private Integer sortNo;
    }
    @Data public static class QuantityRight {
        private Long rightId; private String brandId; private Long goodsId; private String sourceOrderNo;
        private Integer grantedQuantity; private Integer pickedQuantity; private Integer remainingQuantity; private String status;
    }
    @Data public static class AmountRight {
        private Long rightId; private String brandId; private String tierCode; private String tierName;
        private String pricingLevelCode; private BigDecimal grantedAmount; private BigDecimal remainingAmount;
        private String sourceReceiptNo; private String status;
    }
    @Data public static class TargetPlan {
        private Long planId; private String brandId; private String currentLevelCode; private String targetTierCode;
        private String targetTierName; private BigDecimal targetAmount; private BigDecimal progressAmount; private String status;
        private LocalDateTime confirmedTime; private String remark;
    }
}
