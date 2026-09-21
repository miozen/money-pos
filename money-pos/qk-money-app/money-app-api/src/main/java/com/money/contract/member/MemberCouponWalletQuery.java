package com.money.contract.member;

import java.util.Map;

/** UMS-owned read model of a member's unused coupons, grouped by rule. */
public interface MemberCouponWalletQuery {

    Map<Long, Long> countUnusedCouponsByRuleId(Long memberId);
}
