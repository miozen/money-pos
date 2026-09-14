package com.money.feature.trade.application.checkout;

import com.money.entity.OmsOrder;
import com.money.entity.UmsMember;
import com.money.feature.trade.application.boundary.facade.MemberAssetFacade;
import com.money.feature.trade.application.boundary.facade.dto.MemberAssetConsumeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckoutMemberAssetService {

    private final MemberAssetFacade memberAssetFacade;

    public void handleAsset(CheckoutContext context) {
        OmsOrder order = context.getOrder();
        UmsMember verifiedMember = context.getMember();
        if (!order.getVip() || verifiedMember == null) {
            return;
        }

        MemberAssetConsumeRequest request = new MemberAssetConsumeRequest();
        request.setMemberId(verifiedMember.getId());
        request.setCouponRuleId(context.getRequest().getUsedCouponRuleId());
        request.setCouponCount(context.getRequest().getUsedCouponCount());
        request.setFinalPayAmount(context.getPricingResult().getFinalPayAmount());
        request.setMemberCouponDeduct(context.getPricingResult().getActualCouponDeduct());
        request.setPaymentResult(context.getPaymentResult());
        request.setOrderNo(order.getOrderNo());
        memberAssetFacade.consumeForSettlement(request);
    }
}
