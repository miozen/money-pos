package com.money.contract.member;

/** UMS-owned read boundary for a member AMOUNT right. */
public interface MemberAmountRightQuery {
    MemberAmountRightSnapshot findAvailableForPickup(Long memberId, Long rightId);
}
