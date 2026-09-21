package com.money.feature.trade.application.coupon;

import com.money.contract.member.CouponRuleManagementCommand;
import com.money.contract.member.CouponRuleManagementSnapshot;
import com.money.contract.member.MemberCouponRuleSnapshot;
import com.money.web.vo.PageVO;

import java.util.List;

/**
 * 满减券规则管理与会员卡包查询的应用服务边界。
 */
public interface CouponRuleManagementService {

    PageVO<CouponRuleManagementSnapshot> list(Integer current, Integer size, String name);

    void add(CouponRuleManagementCommand command);

    void update(CouponRuleManagementCommand command);

    void delete(List<Long> ids);

    List<MemberCouponRuleSnapshot> getMemberCoupons(Long memberId);
}
