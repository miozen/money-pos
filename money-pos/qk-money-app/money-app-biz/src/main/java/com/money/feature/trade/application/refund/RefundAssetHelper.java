package com.money.feature.trade.application.checkout.refund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderPay;
import com.money.mapper.OmsOrderPayMapper;
import com.money.feature.trade.application.boundary.facade.MemberAssetFacade;
import com.money.feature.trade.application.boundary.facade.dto.MemberAssetRefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RefundAssetHelper {

    private final MemberAssetFacade memberAssetFacade;
    private final OmsOrderPayMapper omsOrderPayMapper;

    public void processFullOrderAsset(OmsOrder order, String orderNo) {
        if (order.getMemberId() == null) {
            return;
        }
        List<OmsOrderPay> pays = omsOrderPayMapper.selectList(new LambdaQueryWrapper<OmsOrderPay>()
                .eq(OmsOrderPay::getOrderNo, orderNo));
        MemberAssetRefundRequest request = buildRequest(
                order.getMemberId(),
                order.getFinalSalesAmount(),
                order.getCouponAmount(),
                true,
                orderNo
        );
        request.setRestoreVouchers(order.getUseVoucherAmount() != null
                && order.getUseVoucherAmount().compareTo(BigDecimal.ZERO) > 0);
        request.setBalancePayments(pays.stream().map(this::toBalancePayment).collect(Collectors.toList()));
        memberAssetFacade.restoreForRefund(request);
    }

    public void processPartialReturnAsset(OmsOrder order, BigDecimal refundSales, BigDecimal refundMemberCoupon, String orderNo) {
        if (order.getMemberId() == null) {
            return;
        }
        memberAssetFacade.restoreForRefund(buildRequest(
                order.getMemberId(), refundSales, refundMemberCoupon, false, orderNo));
    }

    private MemberAssetRefundRequest buildRequest(Long memberId, BigDecimal salesAmount,
                                                  BigDecimal memberCouponAmount, boolean increaseCancelTimes,
                                                  String orderNo) {
        MemberAssetRefundRequest request = new MemberAssetRefundRequest();
        request.setMemberId(memberId);
        request.setSalesAmount(salesAmount);
        request.setMemberCouponAmount(memberCouponAmount);
        request.setIncreaseCancelTimes(increaseCancelTimes);
        request.setOrderNo(orderNo);
        return request;
    }

    private MemberAssetRefundRequest.BalancePayment toBalancePayment(OmsOrderPay pay) {
        MemberAssetRefundRequest.BalancePayment payment = new MemberAssetRefundRequest.BalancePayment();
        payment.setPayMethodCode(pay.getPayMethodCode());
        payment.setPayAmount(pay.getPayAmount());
        return payment;
    }
}
