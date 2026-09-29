package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** Entity-free TARGET plan view for TRADE orchestration. */
@Data
public class MemberTargetPlanSnapshot {
    private Long planId; private Long memberId; private String brandId; private String targetTierCode;
    private BigDecimal targetAmount; private BigDecimal progressAmount; private String status;
}
