package com.money.contract.member;

import java.util.List;

/** Read-only UMS view used to prepare a member deferred pickup. */
public interface MemberQuantityRightQuery {
    List<MemberQuantityRightSnapshot> findAvailableForPickup(Long memberId, List<Long> rightIds);
}
