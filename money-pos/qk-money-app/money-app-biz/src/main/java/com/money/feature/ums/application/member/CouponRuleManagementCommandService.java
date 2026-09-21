package com.money.feature.ums.application.member;

import com.money.contract.member.CouponRuleManagementCommand;
import com.money.contract.member.CouponRuleManagementCommandHandler;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.mapper.PosCouponRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UMS implementation of the Entity-free legacy coupon-rule write boundary. */
@Service
@RequiredArgsConstructor
class CouponRuleManagementCommandService implements CouponRuleManagementCommandHandler {

    private final PosCouponRuleMapper posCouponRuleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CouponRuleManagementCommand command) {
        PosCouponRule rule = toEntity(command);
        posCouponRuleMapper.insert(rule);
        command.setId(rule.getId());
        command.setCreateTime(rule.getCreateTime());
        command.setUpdateTime(rule.getUpdateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CouponRuleManagementCommand command) {
        posCouponRuleMapper.updateById(toEntity(command));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        posCouponRuleMapper.deleteBatchIds(ids);
    }

    private PosCouponRule toEntity(CouponRuleManagementCommand command) {
        PosCouponRule rule = new PosCouponRule();
        rule.setId(command.getId());
        rule.setName(command.getName());
        rule.setThresholdAmount(command.getThresholdAmount());
        rule.setDiscountAmount(command.getDiscountAmount());
        rule.setStatus(command.getStatus());
        rule.setCreateBy(command.getCreateBy());
        rule.setCreateTime(command.getCreateTime());
        rule.setUpdateTime(command.getUpdateTime());
        rule.setTenantId(command.getTenantId());
        return rule;
    }
}
