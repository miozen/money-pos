package com.money.dto.memberbenefit;

import lombok.Data;
import java.math.BigDecimal;

/** Business-only TARGET plan choice used by normal POS checkout. */
@Data
public class MemberTargetPlanOptionVO {
    private Long planId;
    private String brandId;
    private String brandName;
    private String targetTierName;
    private BigDecimal targetAmount;
    private BigDecimal progressAmount;
    private BigDecimal remainingAmount;
}
