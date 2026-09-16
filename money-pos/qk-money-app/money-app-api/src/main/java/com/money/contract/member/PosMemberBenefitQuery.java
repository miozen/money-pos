package com.money.contract.member;

import java.util.List;
import java.util.Map;

/** Read-only UMS benefits required by POS member display. */
public interface PosMemberBenefitQuery {

    Map<Long, PosMemberBenefitSnapshot> findForMembers(List<Long> memberIds);

    List<PosCouponRuleSnapshot> listCouponRules();
}
