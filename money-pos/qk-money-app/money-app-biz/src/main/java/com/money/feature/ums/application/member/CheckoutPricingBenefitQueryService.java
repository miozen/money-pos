package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.CheckoutPricingBenefitQuery;
import com.money.contract.member.CheckoutPricingBenefitSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UMS implementation of the narrow benefit read required for checkout pricing. */
@Service
@RequiredArgsConstructor
class CheckoutPricingBenefitQueryService implements CheckoutPricingBenefitQuery {

    private final UmsMemberBrandLevelMapper memberBrandLevelMapper;
    private final PosCouponRuleMapper couponRuleMapper;

    @Override
    public CheckoutPricingBenefitSnapshot findForPricing(Long memberId, Long voucherRuleId) {
        return new CheckoutPricingBenefitSnapshot(loadMemberBrandLevels(memberId), loadVoucherRule(voucherRuleId));
    }

    private Map<String, String> loadMemberBrandLevels(Long memberId) {
        if (memberId == null) {
            return Collections.emptyMap();
        }
        List<UmsMemberBrandLevel> levels = memberBrandLevelMapper.selectList(
                new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, memberId));
        Map<String, String> result = new LinkedHashMap<>();
        for (UmsMemberBrandLevel level : levels) {
            if (level.getBrand() != null) {
                result.put(level.getBrand().trim(), level.getLevelCode());
            }
        }
        return result;
    }

    private CheckoutPricingBenefitSnapshot.VoucherRule loadVoucherRule(Long voucherRuleId) {
        if (voucherRuleId == null) {
            return null;
        }
        PosCouponRule rule = couponRuleMapper.selectById(voucherRuleId);
        if (rule == null) {
            return null;
        }
        return new CheckoutPricingBenefitSnapshot.VoucherRule(rule.getThresholdAmount(), rule.getDiscountAmount());
    }
}
