package com.money.feature.trade.application.boundary.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.PayMethodEnum;
import com.money.dto.pos.PricingResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.entity.PosMemberCoupon;
import com.money.service.UmsMemberAssetService;
import com.money.feature.trade.application.boundary.facade.dto.MemberAssetConsumeRequest;
import com.money.feature.trade.application.boundary.facade.dto.MemberAssetRefundRequest;
import com.money.feature.trade.application.support.PosAssetActionService;
import com.money.mapper.PosMemberCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Boundary for transaction use cases that change UMS member assets. */
@Service
@RequiredArgsConstructor
public class MemberAssetFacade {

    private final PosAssetActionService assetActionService;
    private final UmsMemberAssetService umsMemberAssetService;
    private final PosMemberCouponMapper posMemberCouponMapper;

    public void consumeForSettlement(MemberAssetConsumeRequest request) {
        SettleAccountsDTO settleRequest = new SettleAccountsDTO();
        settleRequest.setUsedCouponRuleId(request.getCouponRuleId());
        settleRequest.setUsedCouponCount(request.getCouponCount());

        PricingResult pricingResult = new PricingResult();
        pricingResult.setFinalPayAmount(request.getFinalPayAmount());
        pricingResult.setActualCouponDeduct(request.getMemberCouponDeduct());

        assetActionService.consume(
                request.getMemberId(),
                settleRequest,
                pricingResult,
                request.getPaymentResult(),
                request.getOrderNo()
        );
    }

    public void restoreForRefund(MemberAssetRefundRequest request) {
        if (request.getMemberId() == null) {
            return;
        }

        umsMemberAssetService.processReturn(
                request.getMemberId(),
                request.getSalesAmount(),
                request.getMemberCouponAmount(),
                request.isIncreaseCancelTimes(),
                request.getOrderNo()
        );

        if (request.isRestoreVouchers()) {
            Long voucherCount = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getOrderNo, request.getOrderNo()));
            if (voucherCount != null && voucherCount > 0) {
                posMemberCouponMapper.update(null, new LambdaUpdateWrapper<PosMemberCoupon>()
                        .eq(PosMemberCoupon::getOrderNo, request.getOrderNo())
                        .set(PosMemberCoupon::getStatus, "UNUSED")
                        .set(PosMemberCoupon::getUseTime, null)
                        .set(PosMemberCoupon::getOrderNo, null));

                Long totalUnusedVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                        .eq(PosMemberCoupon::getMemberId, request.getMemberId())
                        .eq(PosMemberCoupon::getStatus, "UNUSED"));
                umsMemberAssetService.logVoucherRefund(
                        request.getMemberId(),
                        new BigDecimal(voucherCount),
                        new BigDecimal(totalUnusedVouchers),
                        request.getOrderNo()
                );
            }
        }

        if (request.getBalancePayments() == null) {
            return;
        }
        for (MemberAssetRefundRequest.BalancePayment payment : request.getBalancePayments()) {
            if (PayMethodEnum.fromCode(payment.getPayMethodCode()) == PayMethodEnum.BALANCE) {
                umsMemberAssetService.addBalance(
                        request.getMemberId(),
                        payment.getPayAmount(),
                        request.getOrderNo(),
                        "整单退款:返还余额"
                );
            }
        }
    }
}
