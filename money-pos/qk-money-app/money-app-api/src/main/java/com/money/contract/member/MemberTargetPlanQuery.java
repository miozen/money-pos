package com.money.contract.member;

import java.util.Collection;
import java.util.List;

/** UMS-owned TARGET plan lookup. */
public interface MemberTargetPlanQuery {
    MemberTargetPlanSnapshot findById(Long planId);
    /** Eligible plans for automatic ordinary-checkout contribution, grouped by their own brand by TRADE. */
    List<MemberTargetPlanSnapshot> findEligibleForAutomaticContribution(Long memberId, Collection<String> brandIds);
}
