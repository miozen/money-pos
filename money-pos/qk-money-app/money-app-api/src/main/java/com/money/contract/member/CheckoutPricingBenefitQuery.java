package com.money.contract.member;

/** UMS-owned benefits required by a single checkout pricing calculation. */
public interface CheckoutPricingBenefitQuery {
    CheckoutPricingBenefitSnapshot findForPricing(Long memberId, Long voucherRuleId);
}
