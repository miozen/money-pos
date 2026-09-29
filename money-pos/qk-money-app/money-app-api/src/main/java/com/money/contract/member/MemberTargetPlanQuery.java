package com.money.contract.member;

/** UMS-owned TARGET plan lookup. */
public interface MemberTargetPlanQuery {
    MemberTargetPlanSnapshot findById(Long planId);
}
