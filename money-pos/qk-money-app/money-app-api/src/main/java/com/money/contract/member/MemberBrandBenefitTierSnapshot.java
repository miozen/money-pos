package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** Enabled brand-benefit tier quote used to create an AMOUNT package receipt. */
@Data
public class MemberBrandBenefitTierSnapshot {
    private String brandId;
    private String tierCode;
    private String tierName;
    private BigDecimal configuredAmount;
}
