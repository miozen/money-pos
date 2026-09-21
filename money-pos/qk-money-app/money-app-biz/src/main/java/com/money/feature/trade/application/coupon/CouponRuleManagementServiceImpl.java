package com.money.feature.trade.application.coupon;

import com.money.contract.member.CouponRuleManagementCommand;
import com.money.contract.member.CouponRuleManagementCommandHandler;
import com.money.contract.member.CouponRuleManagementQuery;
import com.money.contract.member.CouponRuleManagementSnapshot;
import com.money.contract.member.MemberCouponRuleSnapshot;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponRuleManagementServiceImpl implements CouponRuleManagementService {

    private final CouponRuleManagementQuery couponRuleManagementQuery;
    private final CouponRuleManagementCommandHandler couponRuleManagementCommandHandler;

    @Override
    public PageVO<CouponRuleManagementSnapshot> list(Integer current, Integer size, String name) {
        return couponRuleManagementQuery.list(current, size, name);
    }

    @Override
    public void add(CouponRuleManagementCommand command) {
        couponRuleManagementCommandHandler.create(command);
    }

    @Override
    public void update(CouponRuleManagementCommand command) {
        couponRuleManagementCommandHandler.update(command);
    }

    @Override
    public void delete(List<Long> ids) {
        couponRuleManagementCommandHandler.delete(ids);
    }

    @Override
    public List<MemberCouponRuleSnapshot> getMemberCoupons(Long memberId) {
        return couponRuleManagementQuery.listMemberCoupons(memberId);
    }
}
