package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.contract.member.PosCouponRuleSnapshot;
import com.money.contract.member.PosMemberBenefitQuery;
import com.money.contract.member.PosMemberBenefitSnapshot;
import com.money.entity.PosCouponRule;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** UMS implementation of the read model required by POS member display. */
@Service
@RequiredArgsConstructor
class PosMemberBenefitQueryService implements PosMemberBenefitQuery {

    private final PosMemberCouponMapper memberCouponMapper;
    private final PosCouponRuleMapper couponRuleMapper;

    @Override
    public Map<Long, PosMemberBenefitSnapshot> findForMembers(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PosMemberCoupon> coupons = memberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .in(PosMemberCoupon::getMemberId, memberIds)
                .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));
        if (coupons.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, PosCouponRule> rulesById = findRulesByIds(coupons.stream()
                .map(PosMemberCoupon::getRuleId).distinct().collect(Collectors.toList()));
        Map<Long, List<PosMemberCoupon>> couponsByMember = coupons.stream()
                .collect(Collectors.groupingBy(PosMemberCoupon::getMemberId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, PosMemberBenefitSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<PosMemberCoupon>> entry : couponsByMember.entrySet()) {
            Map<Long, Long> countByRule = entry.getValue().stream().collect(Collectors.groupingBy(
                    PosMemberCoupon::getRuleId, LinkedHashMap::new, Collectors.counting()));
            List<PosCouponRuleSnapshot> ruleSnapshots = new ArrayList<>();
            for (Map.Entry<Long, Long> count : countByRule.entrySet()) {
                PosCouponRule rule = rulesById.get(count.getKey());
                if (rule != null) {
                    ruleSnapshots.add(toSnapshot(rule, count.getValue().intValue()));
                }
            }
            result.put(entry.getKey(), new PosMemberBenefitSnapshot(entry.getValue().size(), ruleSnapshots));
        }
        return result;
    }

    @Override
    public List<PosCouponRuleSnapshot> listCouponRules() {
        return couponRuleMapper.selectList(new LambdaQueryWrapper<PosCouponRule>().orderByDesc(PosCouponRule::getId))
                .stream().map(rule -> toSnapshot(rule, 0)).collect(Collectors.toList());
    }

    private Map<Long, PosCouponRule> findRulesByIds(Collection<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return couponRuleMapper.selectBatchIds(ruleIds).stream().collect(Collectors.toMap(
                PosCouponRule::getId, rule -> rule, (left, ignored) -> left, LinkedHashMap::new));
    }

    private PosCouponRuleSnapshot toSnapshot(PosCouponRule rule, int availableCount) {
        return new PosCouponRuleSnapshot(rule.getId(), rule.getName(), rule.getThresholdAmount(),
                rule.getDiscountAmount(), rule.getStatus(), availableCount);
    }
}
