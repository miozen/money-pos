package com.money.contract.member;

/** UMS-owned enabled-tier lookup; callers may not read UMS tier entities directly. */
public interface MemberBrandBenefitTierQuery {
    MemberBrandBenefitTierSnapshot findEnabled(String brandId, String tierCode);
}
