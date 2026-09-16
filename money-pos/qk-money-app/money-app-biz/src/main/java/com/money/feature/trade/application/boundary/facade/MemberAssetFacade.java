package com.money.feature.trade.application.boundary.facade;

import com.money.constant.PayMethodEnum;
import com.money.contract.member.MemberRefundCommand;
import com.money.contract.member.MemberRefundCommandHandler;
import com.money.contract.member.MemberSettlementCommand;
import com.money.contract.member.MemberSettlementCommandHandler;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.feature.trade.application.boundary.facade.dto.MemberAssetConsumeRequest;
import com.money.feature.trade.application.boundary.facade.dto.MemberAssetRefundRequest;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Boundary for transaction use cases that change UMS member assets. */
@Service
@RequiredArgsConstructor
public class MemberAssetFacade {

    private final MemberSettlementCommandHandler memberSettlementCommandHandler;
    private final MemberRefundCommandHandler memberRefundCommandHandler;

    public void consumeForSettlement(MemberAssetConsumeRequest request) {
        MemberSettlementCommand command = new MemberSettlementCommand();
        command.setMemberId(request.getMemberId());
        command.setOrderNo(request.getOrderNo());
        command.setFinalPayAmount(request.getFinalPayAmount());
        command.setMemberCouponDeduct(request.getMemberCouponDeduct());
        command.setVoucherRuleId(request.getCouponRuleId());
        command.setVoucherCount(request.getCouponCount());

        NormalizedPaymentResult paymentResult = request.getPaymentResult();
        if (paymentResult == null || paymentResult.getValidItems() == null) {
            throw new BaseException("系统内部流水线异常：支付凭证脱落，请重试！");
        }
        BigDecimal balanceAmount = paymentResult.getValidItems().stream()
                .filter(item -> PayMethodEnum.fromCode(item.getMethodCode()) == PayMethodEnum.BALANCE)
                .map(item -> item.getNetAmount() == null ? BigDecimal.ZERO : item.getNetAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        command.setBalancePaymentRequested(paymentResult.getValidItems().stream()
                .anyMatch(item -> PayMethodEnum.fromCode(item.getMethodCode()) == PayMethodEnum.BALANCE));
        command.setBalancePaymentAmount(balanceAmount);
        memberSettlementCommandHandler.handle(command);
    }

    public void restoreForRefund(MemberAssetRefundRequest request) {
        if (request.getMemberId() == null) {
            return;
        }

        MemberRefundCommand command = new MemberRefundCommand();
        command.setMemberId(request.getMemberId());
        command.setOrderNo(request.getOrderNo());
        command.setSalesAmount(request.getSalesAmount());
        command.setMemberCouponRefund(request.getMemberCouponAmount());
        command.setIncreaseCancelTimes(request.isIncreaseCancelTimes());
        command.setRestoreVouchers(request.isRestoreVouchers());
        command.setBalanceRefundAmount(balanceRefundAmount(request));
        memberRefundCommandHandler.handle(command);
    }

    private BigDecimal balanceRefundAmount(MemberAssetRefundRequest request) {
        if (request.getBalancePayments() == null) {
            return BigDecimal.ZERO;
        }
        return request.getBalancePayments().stream()
                .filter(payment -> PayMethodEnum.fromCode(payment.getPayMethodCode()) == PayMethodEnum.BALANCE)
                .map(payment -> payment.getPayAmount() == null ? BigDecimal.ZERO : payment.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
