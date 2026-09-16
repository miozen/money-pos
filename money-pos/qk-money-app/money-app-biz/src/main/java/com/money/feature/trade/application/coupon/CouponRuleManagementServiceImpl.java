package com.money.feature.trade.application.coupon;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.entity.PosCouponRule;
import com.money.entity.PosMemberCoupon;
import com.money.mapper.PosCouponRuleMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponRuleManagementServiceImpl implements CouponRuleManagementService {

    private final PosCouponRuleMapper posCouponRuleMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    @Override
    public PageVO<PosCouponRule> list(Integer current, Integer size, String name) {
        LambdaQueryWrapper<PosCouponRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(name), PosCouponRule::getName, name)
                .orderByDesc(PosCouponRule::getCreateTime);
        Page<PosCouponRule> pageResult = posCouponRuleMapper.selectPage(new Page<>(current, size), queryWrapper);
        return PageUtil.toPageVO(pageResult, entity -> entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(PosCouponRule rule) {
        posCouponRuleMapper.insert(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PosCouponRule rule) {
        posCouponRuleMapper.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        posCouponRuleMapper.deleteBatchIds(ids);
    }

    @Override
    public List<Map<String, Object>> getMemberCoupons(Long memberId) {
        LambdaQueryWrapper<PosMemberCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PosMemberCoupon::getMemberId, memberId)
                .eq(PosMemberCoupon::getStatus, "UNUSED");
        List<PosMemberCoupon> coupons = posMemberCouponMapper.selectList(wrapper);
        if (coupons == null || coupons.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Long> ruleCountMap = coupons.stream()
                .collect(Collectors.groupingBy(PosMemberCoupon::getRuleId, Collectors.counting()));
        List<PosCouponRule> rules = posCouponRuleMapper.selectBatchIds(ruleCountMap.keySet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (PosCouponRule rule : rules) {
            Map<String, Object> map = new HashMap<>();
            map.put("ruleId", rule.getId());
            map.put("name", rule.getName());
            map.put("thresholdAmount", rule.getThresholdAmount());
            map.put("discountAmount", rule.getDiscountAmount());
            map.put("ownedCount", ruleCountMap.get(rule.getId()));
            result.add(map);
        }
        return result;
    }
}
