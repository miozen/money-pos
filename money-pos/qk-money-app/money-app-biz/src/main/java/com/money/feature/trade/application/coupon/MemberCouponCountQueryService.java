package com.money.feature.trade.application.coupon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberCouponCountQuery;
import com.money.entity.PosMemberCoupon;
import com.money.mapper.PosMemberCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/** TRADE 对会员满减券数量中立查询契约的实现。 */
@Service
@RequiredArgsConstructor
class MemberCouponCountQueryService implements MemberCouponCountQuery {

    private final PosMemberCouponMapper posMemberCouponMapper;

    @Override
    public Map<Long, Long> countUnusedCouponsByMemberIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                        .in(PosMemberCoupon::getMemberId, memberIds)
                        .eq(PosMemberCoupon::getStatus, "UNUSED"))
                .stream()
                .collect(Collectors.groupingBy(PosMemberCoupon::getMemberId, Collectors.counting()));
    }
}
