package com.money.feature.ums.application.memberasset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.contract.member.MemberSettlementCommand;
import com.money.contract.member.MemberSettlementCommandHandler;
import com.money.entity.PosMemberCoupon;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** UMS 对会员结算命令的实现。 */
@Service
@RequiredArgsConstructor
class MemberSettlementCommandService implements MemberSettlementCommandHandler {
    private final UmsMemberAssetService memberAssetService;
    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final PosMemberCouponMapper posMemberCouponMapper;

    @Override
    public void handle(MemberSettlementCommand command) {
        if (command == null || command.getMemberId() == null || command.getOrderNo() == null) {
            throw new BaseException("会员结算命令不完整");
        }
        LocalDateTime now = LocalDateTime.now();
        memberAssetService.consume(
                command.getMemberId(), command.getFinalPayAmount(), command.getMemberCouponDeduct(), command.getOrderNo());
        consumeVouchers(command, now);
        BigDecimal balanceAmount = command.getBalancePaymentAmount() == null
                ? BigDecimal.ZERO : command.getBalancePaymentAmount();
        if (command.isBalancePaymentRequested() && balanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException("【风控拦截】账务异常：余额扣除指令未成功下达！");
        }
        BigDecimal finalPay = command.getFinalPayAmount() == null ? BigDecimal.ZERO : command.getFinalPayAmount();
        if (balanceAmount.compareTo(finalPay) > 0) {
            throw new BaseException("【风控拦截】余额支付金额异常，超出了本单应收底线！");
        }
        if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
            memberAssetService.deductBalance(command.getMemberId(), balanceAmount, command.getOrderNo(), "收银台支付扣除");
        }
        UmsMember update = new UmsMember();
        update.setId(command.getMemberId());
        update.setLastVisitTime(now);
        umsMemberMapper.updateById(update);
    }

    private void consumeVouchers(MemberSettlementCommand command, LocalDateTime now) {
        int count = command.getVoucherCount() == null ? 0 : command.getVoucherCount();
        if (command.getVoucherRuleId() == null || count <= 0) {
            return;
        }
        if (count > 500) {
            throw new BaseException("【风控拦截】单次核销满减券数量超出安全阈值");
        }
        List<PosMemberCoupon> available = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, command.getMemberId())
                .eq(PosMemberCoupon::getRuleId, command.getVoucherRuleId())
                .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name())
                .orderByAsc(PosMemberCoupon::getGetTime, PosMemberCoupon::getId)
                .last("LIMIT " + count));
        if (available.size() < count) {
            throw new BaseException("【满减券异常】可用数量不足或已被使用。");
        }
        PosMemberCoupon update = new PosMemberCoupon();
        update.setStatus(CouponStatusEnum.USED.name());
        update.setOrderNo(command.getOrderNo());
        update.setUseTime(now);
        int rows = posMemberCouponMapper.update(update, new LambdaUpdateWrapper<PosMemberCoupon>()
                .in(PosMemberCoupon::getId, available.stream().map(PosMemberCoupon::getId).collect(Collectors.toList()))
                .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));
        if (rows != count) {
            throw new BaseException("【并发拦截】优惠券已被抢占，操作回滚。");
        }
        long remaining = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                .eq(PosMemberCoupon::getMemberId, command.getMemberId())
                .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));
        UmsMember member = umsMemberMapper.selectById(command.getMemberId());
        UmsMemberLog log = new UmsMemberLog();
        log.setMemberId(command.getMemberId());
        if (member != null) {
            log.setMemberName(member.getName());
            log.setMemberPhone(member.getPhone());
        }
        log.setType("VOUCHER");
        log.setOperateType("CONSUME");
        log.setAmount(BigDecimal.valueOf(-count));
        log.setAfterAmount(BigDecimal.valueOf(remaining));
        log.setOrderNo(command.getOrderNo());
        log.setRemark(String.format("核销满减券 | 规则ID:%s | 张数:%s", command.getVoucherRuleId(), count));
        log.setCreateTime(now);
        umsMemberLogMapper.insert(log);
    }
}
