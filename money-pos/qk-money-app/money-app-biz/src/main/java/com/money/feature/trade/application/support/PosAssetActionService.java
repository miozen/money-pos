package com.money.feature.trade.application.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.money.constant.CouponStatusEnum;
import com.money.constant.PayMethodEnum;
import com.money.dto.pos.NormalizedPaymentResult;
import com.money.dto.pos.PricingResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.entity.PosMemberCoupon;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.feature.ums.application.member.UmsMemberService;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosAssetActionService {
    private static final String LOG_TYPE_VOUCHER = "VOUCHER";
    private static final String OPERATE_CONSUME = "CONSUME";

    private final UmsMemberService umsMemberService;
    private final PosMemberCouponMapper posMemberCouponMapper;
    private final UmsMemberLogMapper umsMemberLogMapper;

    public void consume(Long memberId, SettleAccountsDTO dto, PricingResult trialRes, NormalizedPaymentResult payResult, String orderNo) {
        // 安全拦截：防止流水线编排异常导致 payResult 为空
        if (payResult == null || payResult.getValidItems() == null) {
            log.error("【极度危险】资产管家收到了空的支付凭证！单号: {}", orderNo);
            throw new BaseException("系统内部流水线异常：支付凭证脱落，请重试！");
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. 记录会员消费统计 (此方法仅记录消费额，不扣除余额)
        umsMemberService.consume(memberId, trialRes.getFinalPayAmount(), trialRes.getActualCouponDeduct(), orderNo);

        // 2. 满减券物理核销与流水记录
        if (dto.getUsedCouponRuleId() != null && dto.getUsedCouponCount() != null && dto.getUsedCouponCount() > 0) {
            int count = dto.getUsedCouponCount();

            if (count > 500) {
                throw new BaseException("【风控拦截】单次核销满减券数量超出安全阈值");
            }

            List<PosMemberCoupon> availableCoupons = posMemberCouponMapper.selectList(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, memberId)
                    .eq(PosMemberCoupon::getRuleId, dto.getUsedCouponRuleId())
                    .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name())
                    .orderByAsc(PosMemberCoupon::getGetTime, PosMemberCoupon::getId)
                    .last("LIMIT " + count));

            if (availableCoupons.size() < count) throw new BaseException("【满减券异常】可用数量不足或已被使用。");

            List<Long> couponIds = availableCoupons.stream().map(PosMemberCoupon::getId).collect(Collectors.toList());
            PosMemberCoupon updateCoupon = new PosMemberCoupon();
            updateCoupon.setStatus(CouponStatusEnum.USED.name());
            updateCoupon.setOrderNo(orderNo);
            updateCoupon.setUseTime(now);

            int rows = posMemberCouponMapper.update(updateCoupon, new LambdaUpdateWrapper<PosMemberCoupon>()
                    .in(PosMemberCoupon::getId, couponIds).eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));
            if (rows != count) throw new BaseException("【并发拦截】优惠券已被抢占，操作回滚。");

            long remainVouchers = posMemberCouponMapper.selectCount(new LambdaQueryWrapper<PosMemberCoupon>()
                    .eq(PosMemberCoupon::getMemberId, memberId)
                    .eq(PosMemberCoupon::getStatus, CouponStatusEnum.UNUSED.name()));

            UmsMember member = umsMemberService.getById(memberId);

            UmsMemberLog voucherLog = new UmsMemberLog();
            voucherLog.setMemberId(memberId);
            if (member != null) {
                voucherLog.setMemberName(member.getName());
                voucherLog.setMemberPhone(member.getPhone());
            }
            voucherLog.setType(LOG_TYPE_VOUCHER);
            voucherLog.setOperateType(OPERATE_CONSUME);
            voucherLog.setAmount(BigDecimal.valueOf(-count));
            voucherLog.setAfterAmount(BigDecimal.valueOf(remainVouchers));
            voucherLog.setOrderNo(orderNo);
            voucherLog.setRemark(String.format("核销满减券 | 规则ID:%s | 张数:%s", dto.getUsedCouponRuleId(), count));
            voucherLog.setCreateTime(now);
            umsMemberLogMapper.insert(voucherLog);
        }

        // ==========================================
        // 🌟 P0 核心修复 3：业务逻辑书同文，使用统一的枚举解析取代硬编码比对！
        // ==========================================
        BigDecimal balanceCost = payResult.getValidItems().stream()
                .filter(p -> PayMethodEnum.fromCode(p.getMethodCode()) == PayMethodEnum.BALANCE)
                .map(NormalizedPaymentResult.StandardPayItem::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 🌟 核心防线：资金逻辑断言！只要检测到有 BALANCE 支付意向，扣除额必须大于0
        boolean hasBalanceIntent = payResult.getValidItems().stream()
                .anyMatch(p -> PayMethodEnum.fromCode(p.getMethodCode()) == PayMethodEnum.BALANCE);

        if (hasBalanceIntent && balanceCost.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("【资金防漏拦截】检测到余额支付意图，但实际核算出的扣除额为0！单号: {}", orderNo);
            throw new BaseException("【风控拦截】账务异常：余额扣除指令未成功下达！");
        }

        // 3. 真正执行余额物理扣减
        if (balanceCost.compareTo(BigDecimal.ZERO) > 0) {
            if (balanceCost.compareTo(payResult.getNetReceived()) > 0) {
                log.error("【资产安全拦截】尝试扣减的余额({})大于订单净入账({})，单号: {}", balanceCost, payResult.getNetReceived(), orderNo);
                throw new BaseException("【风控拦截】余额支付金额异常，超出了本单应收底线！");
            }
            umsMemberService.deductBalance(memberId, balanceCost, orderNo, "收银台支付扣除");
        }

        // 4. 更新到店时间
        boolean updateSuccess = umsMemberService.lambdaUpdate()
                .set(UmsMember::getLastVisitTime, now)
                .eq(UmsMember::getId, memberId)
                .update();

        if (!updateSuccess) {
            log.warn("【数据一致性预警】订单 {} 结算成功，但更新会员(ID:{})最后到店时间失败，该会员可能刚刚被删除或冻结。", orderNo, memberId);
        }
    }
}
