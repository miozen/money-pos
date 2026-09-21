package com.money.feature.ums.application.memberasset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.constant.BizErrorStatus;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
import com.money.feature.ums.infrastructure.persistence.entity.UmsRechargeOrder;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 领域服务：会员资产子域 (V4.0 防注入安全版)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UmsMemberAssetService {

    private final UmsMemberMapper umsMemberMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final UmsRechargeOrderMapper umsRechargeOrderMapper;

    private static final String TYPE_BALANCE = "BALANCE";
    private static final String TYPE_COUPON = "COUPON";
    private static final String TYPE_VOUCHER = "VOUCHER";

    public List<UmsMemberLog> getMemberLogs(Long memberId) {
        return umsMemberLogMapper.selectList(
                new LambdaQueryWrapper<UmsMemberLog>()
                        .eq(UmsMemberLog::getMemberId, memberId)
                        .orderByDesc(UmsMemberLog::getCreateTime)
                        .last("LIMIT 100")
        );
    }

    public UmsRechargeOrder getRechargeOrderDetail(String orderNo) {
        UmsRechargeOrder order = umsRechargeOrderMapper.selectOne(
                new LambdaQueryWrapper<UmsRechargeOrder>()
                        .eq(UmsRechargeOrder::getOrderNo, orderNo)
                        .last("LIMIT 1")
        );

        if (order == null) {
            log.error("未找到充值凭证，单号：{}", orderNo);
            throw new BaseException("单据档案不存在，可能已被物理删除");
        }
        return order;
    }

    // ==========================================
    // 🌟 正向交易：底层预编译防注入，精准扣库
    // ==========================================
    public void consume(Long id, BigDecimal amount, BigDecimal couponAmount, String orderNo) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) throw new BaseException("扣款金额不能为负数");

        UmsMember member = umsMemberMapper.selectById(id);
        if (member == null) throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "会员不存在");

        BigDecimal beforeCoupon = member.getCoupon() != null ? member.getCoupon() : BigDecimal.ZERO;
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;

        // 🌟 核心替换：使用底层 Mapper 执行原子扣减
        int rows = umsMemberMapper.consumeAsset(id, safeAmount, couponAmount);

        if (rows == 0 && couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BaseException(BizErrorStatus.COUPON_NOT_ENOUGH, "扣款失败：券余额不足");
        }

        if (couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) > 0) {
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "CONSUME", couponAmount.negate(), BigDecimal.ZERO, beforeCoupon.subtract(couponAmount), orderNo, "订单自动抵扣会员券"));
        }
    }

    public void deductBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        UmsMember member = umsMemberMapper.selectById(memberId);
        if (member == null) throw new BaseException(BizErrorStatus.MEMBER_NOT_FOUND, "找不到会员信息");

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;

        // 🌟 核心替换：使用底层 Mapper 执行原子扣减
        int rows = umsMemberMapper.deductBalanceAtomically(memberId, amount);

        if (rows == 0) {
            throw new BaseException(BizErrorStatus.BALANCE_INSUFFICIENT, "余额不足");
        }

        umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "CONSUME", amount.negate(), BigDecimal.ZERO, beforeBalance.subtract(amount), orderNo, remark));
    }

    // ==========================================
    // 逆向退款：优雅降级，放过散客与死号，保全退款主流程！
    // ==========================================
    public void addBalance(Long memberId, BigDecimal amount, String orderNo, String remark) {
        if (memberId == null || memberId <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        UmsMember member = umsMemberMapper.selectById(memberId);
        if (member == null) {
            log.warn("【优雅降级】退还余额时未找到会员(ID:{})，可能为散客或已被删除，跳过资产回退...", memberId);
            return;
        }

        BigDecimal beforeBalance = member.getBalance() != null ? member.getBalance() : BigDecimal.ZERO;

        // 🌟 核心替换：使用底层 Mapper 执行原子增加
        umsMemberMapper.addBalanceAtomically(memberId, amount);

        umsMemberLogMapper.insert(createLog(member, TYPE_BALANCE, "REFUND", amount, BigDecimal.ZERO, beforeBalance.add(amount), orderNo, remark));
    }

    public void logVoucherRefund(Long memberId, BigDecimal amount, BigDecimal afterAmount, String orderNo) {
        if (memberId == null || memberId <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        UmsMember member = umsMemberMapper.selectById(memberId);
        if (member != null) {
            umsMemberLogMapper.insert(createLog(member, TYPE_VOUCHER, "REFUND", amount, BigDecimal.ZERO, afterAmount, orderNo, "整单退款:满减券退回"));
        } else {
            log.warn("【优雅降级】退还满减券时未找到会员(ID:{})，跳过...", memberId);
        }
    }

    public void processReturn(Long id, BigDecimal amount, BigDecimal coupon, boolean increaseCancelTimes, String orderNo) {
        if (id == null || id <= 0) return;

        UmsMember member = umsMemberMapper.selectById(id);
        if (member == null) {
            log.warn("【优雅降级】退货时未找到会员(ID:{})，跳过资产回退，保障资金与库存退库顺畅！", id);
            return;
        }

        BigDecimal beforeCoupon = member.getCoupon() == null ? BigDecimal.ZERO : member.getCoupon();
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;

        if (coupon != null && coupon.compareTo(BigDecimal.ZERO) > 0) {
            umsMemberLogMapper.insert(createLog(member, TYPE_COUPON, "REFUND", coupon, BigDecimal.ZERO, beforeCoupon.add(coupon), orderNo, "售后退货返还会员券"));
        }

        // 🌟 核心替换：使用底层 Mapper 执行原子更新
        int rows = umsMemberMapper.processReturnAsset(id, safeAmount, coupon, increaseCancelTimes);

        if (rows == 0) {
            log.warn("【数据预警】会员(ID:{})资产退回 SQL 未命中任何行", id);
        }
    }

    private UmsMemberLog createLog(UmsMember m, String type, String opType, BigDecimal amt, BigDecimal realAmt, BigDecimal afterAmt, String orderNo, String remark) {
        UmsMemberLog l = new UmsMemberLog();
        l.setMemberId(m.getId());
        l.setMemberName(m.getName());
        l.setMemberPhone(m.getPhone());
        l.setType(type);
        l.setOperateType(opType);
        l.setAmount(amt);
        l.setRealAmount(realAmt);
        l.setAfterAmount(afterAmt);
        l.setOrderNo(orderNo);
        l.setRemark(remark);
        l.setCreateTime(LocalDateTime.now());
        return l;
    }
}
