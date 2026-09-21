package com.money.feature.ums.application.member;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.contract.member.CouponRuleManagementQuery;
import com.money.contract.member.CouponRuleManagementSnapshot;
import com.money.contract.member.MemberCouponRuleSnapshot;
import com.money.contract.member.MemberCouponWalletQuery;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.mapper.PosCouponRuleMapper;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** UMS implementation of the Entity-free legacy coupon-rule read boundary. */
@Service
@RequiredArgsConstructor
class CouponRuleManagementQueryService implements CouponRuleManagementQuery {

    private final PosCouponRuleMapper posCouponRuleMapper;
    private final MemberCouponWalletQuery memberCouponWalletQuery;

    @Override
    public PageVO<CouponRuleManagementSnapshot> list(Integer current, Integer size, String name) {
        LambdaQueryWrapper<PosCouponRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(name), PosCouponRule::getName, name)
                .orderByDesc(PosCouponRule::getCreateTime);
        Page<PosCouponRule> pageResult = posCouponRuleMapper.selectPage(new Page<>(current, size), queryWrapper);
        return PageUtil.toPageVO(pageResult, this::toManagementSnapshot);
    }

    @Override
    public List<MemberCouponRuleSnapshot> listMemberCoupons(Long memberId) {
        Map<Long, Long> ruleCountMap = memberCouponWalletQuery.countUnusedCouponsByRuleId(memberId);
        if (ruleCountMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<PosCouponRule> rules = posCouponRuleMapper.selectBatchIds(ruleCountMap.keySet());
        List<MemberCouponRuleSnapshot> result = new ArrayList<>();
        for (PosCouponRule rule : rules) {
            result.add(new MemberCouponRuleSnapshot(rule.getId(), rule.getName(), rule.getThresholdAmount(),
                    rule.getDiscountAmount(), ruleCountMap.get(rule.getId())));
        }
        return result;
    }

    private CouponRuleManagementSnapshot toManagementSnapshot(PosCouponRule rule) {
        return new CouponRuleManagementSnapshot(rule.getId(), rule.getName(), rule.getThresholdAmount(),
                rule.getDiscountAmount(), rule.getStatus(), rule.getCreateBy(), rule.getCreateTime(),
                rule.getUpdateTime(), rule.getTenantId());
    }
}
