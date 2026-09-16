package com.money.feature.trade.application.coupon;

import com.money.entity.PosCouponRule;
import com.money.web.vo.PageVO;

import java.util.List;
import java.util.Map;

/**
 * 满减券规则管理与会员卡包查询的应用服务边界。
 */
public interface CouponRuleManagementService {

    PageVO<PosCouponRule> list(Integer current, Integer size, String name);

    void add(PosCouponRule rule);

    void update(PosCouponRule rule);

    void delete(List<Long> ids);

    List<Map<String, Object>> getMemberCoupons(Long memberId);
}
