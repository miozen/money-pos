package com.money.contract.member;

import com.money.web.vo.PageVO;

import java.util.List;

/** UMS-owned read boundary for legacy coupon-rule administration. */
public interface CouponRuleManagementQuery {
    PageVO<CouponRuleManagementSnapshot> list(Integer current, Integer size, String name);
    List<MemberCouponRuleSnapshot> listMemberCoupons(Long memberId);
}
