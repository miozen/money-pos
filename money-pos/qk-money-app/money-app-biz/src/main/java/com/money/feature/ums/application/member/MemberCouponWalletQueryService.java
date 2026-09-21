package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberCouponCountQuery;
import com.money.contract.member.MemberCouponWalletQuery;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.mapper.PosMemberCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** UMS implementation of the entity-free unused-coupon query contracts. */
@Service
@RequiredArgsConstructor
class MemberCouponWalletQueryService implements MemberCouponCountQuery, MemberCouponWalletQuery {

    private final PosMemberCouponMapper posMemberCouponMapper;

    @Override
    public Map<Long, Long> countUnusedCouponsByMemberIds(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PosMemberCoupon> coupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .in(PosMemberCoupon::getMemberId, memberIds).eq(PosMemberCoupon::getStatus, "UNUSED"));
        return Collections.unmodifiableMap(coupons.stream().collect(Collectors.groupingBy(
                PosMemberCoupon::getMemberId, LinkedHashMap::new, Collectors.counting())));
    }

    @Override
    public Map<Long, Long> countUnusedCouponsByRuleId(Long memberId) {
        if (memberId == null) {
            return Collections.emptyMap();
        }
        List<PosMemberCoupon> coupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, memberId).eq(PosMemberCoupon::getStatus, "UNUSED"));
        return Collections.unmodifiableMap(coupons.stream().collect(Collectors.groupingBy(
                PosMemberCoupon::getRuleId, LinkedHashMap::new, Collectors.counting())));
    }
}
