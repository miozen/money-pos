package com.money.feature.trade.application.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * POS 与会员权益入口共用的优惠券规则展示契约。
 */
@Data
@AllArgsConstructor
public class CouponRuleSummary {

    private Long id;
    private String name;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private Integer status;
}
