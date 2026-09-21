package com.money.feature.ums.application.memberasset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.contract.member.MemberRefundCommand;
import com.money.contract.member.MemberRefundCommandHandler;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.mapper.PosMemberCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** UMS 对会员退款命令的实现。 */
@Service
@RequiredArgsConstructor
class MemberRefundCommandService implements MemberRefundCommandHandler {
    private final UmsMemberAssetService memberAssetService;
    private final PosMemberCouponMapper posMemberCouponMapper;
    @Override
    public void handle(MemberRefundCommand command) {
        if (command == null || command.getMemberId() == null || command.getOrderNo() == null) {
            return;
        }
        memberAssetService.processReturn(
                command.getMemberId(), command.getSalesAmount(), command.getMemberCouponRefund(),
                command.isIncreaseCancelTimes(), command.getOrderNo());
        if (command.isRestoreVouchers()) {
            restoreVouchers(command);
        }
        if (command.getBalanceRefundAmount() != null
                && command.getBalanceRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            memberAssetService.addBalance(
                    command.getMemberId(), command.getBalanceRefundAmount(), command.getOrderNo(), "整单退款:返还余额");
        }
    }

    private void restoreVouchers(MemberRefundCommand command) {
        Long count = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getOrderNo, command.getOrderNo()));
        if (count == null || count == 0) {
            return;
        }
        posMemberCouponMapper.update(null, new LambdaUpdateWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getOrderNo, command.getOrderNo())
                .set(PosMemberCoupon::getStatus, "UNUSED")
                .set(PosMemberCoupon::getUseTime, null)
                .set(PosMemberCoupon::getOrderNo, null));
        Long unused = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, command.getMemberId())
                .eq(PosMemberCoupon::getStatus, "UNUSED"));
        memberAssetService.logVoucherRefund(command.getMemberId(), new BigDecimal(count), new BigDecimal(unused), command.getOrderNo());
    }
}
