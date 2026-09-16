package com.money.feature.trade.application.coupon;

import java.util.Collection;
import java.util.Map;

/**
 * TRADE 对外提供的会员满减券只读查询边界。
 *
 * <p>只暴露聚合数量，避免其他功能域直接依赖优惠券持久化实体和 Mapper。</p>
 */
public interface MemberCouponQueryService {

    /**
     * 按会员批量统计状态为 UNUSED 的满减券数量。
     */
    Map<Long, Long> countUnusedCouponsByMemberIds(Collection<Long> memberIds);
}
