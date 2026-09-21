package com.money.feature.trade.application.checkout.refund;

import com.money.constant.BizErrorStatus;
import com.money.constant.OrderStatusEnum;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.trade.infrastructure.persistence.entity.OmsRefundIdempotent;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsRefundIdempotentMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 🌟 领域防线：退款状态机与并发拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundStateGuard {

    private final OmsRefundIdempotentMapper idempotentMapper;

    /**
     * 第一道防线：基于数据库唯一索引的强幂等拦截
     */
    public void acquireIdempotent(String reqId, String bizType) {
        try {
            OmsRefundIdempotent record = new OmsRefundIdempotent();
            record.setReqId(reqId);
            record.setBizType(bizType);
            record.setTenantId(0L);
            record.setCreateTime(LocalDateTime.now());
            idempotentMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.warn("【退款拦截】检测到重复点击: {}", reqId);
            throw new BaseException(BizErrorStatus.POS_REFUND_REPEAT).withData("请勿重复点击");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                log.warn("【退款拦截】检测到重复点击(底层): {}", reqId);
                throw new BaseException(BizErrorStatus.POS_REFUND_REPEAT).withData("请勿重复点击");
            }
            throw e;
        }
    }

    /**
     * 第二道防线：状态机校验（未付款、已关闭等状态绝不允许退款）
     */
    public void checkRefundableState(OmsOrder order) {
        if (order == null) {
            throw new BaseException(BizErrorStatus.POS_REFUND_NOT_FOUND, "订单不存在");
        }

        // 只有 PAID 和 PARTIAL_REFUNDED 状态允许发起退款
        String status = order.getStatus();
        if (!OrderStatusEnum.PAID.name().equals(status) && !OrderStatusEnum.PARTIAL_REFUNDED.name().equals(status)) {
            throw new BaseException(BizErrorStatus.POS_REFUND_STATUS_INVALID, "当前状态[" + status + "]不允许退款");
        }
    }
}
