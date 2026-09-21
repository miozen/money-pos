package com.money.feature.trade.application.checkout;

import com.money.contract.member.MemberCheckoutSnapshot;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
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
        MemberCheckoutSnapshot verifiedMember = context.getMember();
        if (!order.getVip() || verifiedMember == null) {
            return;
        }

        MemberAssetConsumeRequest request = new MemberAssetConsumeRequest();
        request.setMemberId(verifiedMember.getMemberId());
        request.setCouponRuleId(context.getRequest().getUsedCouponRuleId());
        request.setCouponCount(context.getRequest().getUsedCouponCount());
        request.setFinalPayAmount(context.getPricingResult().getFinalPayAmount());
        request.setMemberCouponDeduct(context.getPricingResult().getActualCouponDeduct());
        request.setPaymentResult(context.getPaymentResult());
        request.setOrderNo(order.getOrderNo());
        memberAssetFacade.consumeForSettlement(request);
    }
}
