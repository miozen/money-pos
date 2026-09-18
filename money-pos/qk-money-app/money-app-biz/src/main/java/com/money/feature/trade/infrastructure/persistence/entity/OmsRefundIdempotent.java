package com.money.feature.trade.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** TRADE 退款防重放持久化记录，不作为跨 Feature 契约暴露。 */
@Data
@TableName("oms_refund_idempotent")
public class OmsRefundIdempotent {

    private String reqId;
    private String bizType;
    private Long tenantId;
    private LocalDateTime createTime;
}
