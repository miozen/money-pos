package com.money.contract.member;

/** Narrow UMS command boundary used by later QUANTITY/AMOUNT/TARGET application services. */
public interface MemberBrandBenefitLedgerCommandHandler {
    Long grantQuantity(MemberBrandBenefitLedgerCommand.QuantityGrant command);
    void changeQuantity(MemberBrandBenefitLedgerCommand.QuantityChange command);
    Long grantAmount(MemberBrandBenefitLedgerCommand.AmountGrant command);
    void changeAmount(MemberBrandBenefitLedgerCommand.AmountChange command);
    Long createTargetPlan(MemberBrandBenefitLedgerCommand.TargetPlanCreate command);
    void changeTargetProgress(MemberBrandBenefitLedgerCommand.TargetProgressChange command);
    void confirmTargetPlan(Long planId, String requestNo, String operatorName, String reason);
    void markTargetPlanReviewRequired(Long planId, String requestNo, String sourceNo, String reason);
    void cancelTargetPlan(Long planId, String requestNo, String operatorName, String reason);
    void activateTierIfHigher(Long memberId, String brandId, String tierCode);
}
