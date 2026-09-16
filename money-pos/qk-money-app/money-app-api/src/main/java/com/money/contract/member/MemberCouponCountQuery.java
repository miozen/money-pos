package com.money.contract.member;

import java.util.Collection;
import java.util.Map;

/**
 * 会员满减券数量的中立只读契约。
 *
 * <p>该接口不暴露优惠券持久化实体或 Mapper，可由拥有优惠券数据的功能实现，
 * 并供只需要聚合数量的功能消费。</p>
 */
public interface MemberCouponCountQuery {

    /**
     * 按会员批量统计状态为 UNUSED 的满减券数量。
     */
    Map<Long, Long> countUnusedCouponsByMemberIds(Collection<Long> memberIds);
}
