package com.money.contract.member;

import java.util.List;

/** UMS-owned write boundary for coupon-rule administration. */
public interface CouponRuleManagementCommandHandler {
    void create(CouponRuleManagementCommand command);
    void update(CouponRuleManagementCommand command);
    void delete(List<Long> ids);
}
