package com.money.feature.trade.application.boundary.facade.dto;

import com.money.dto.pos.NormalizedPaymentResult;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberAssetConsumeRequest {
    private Long memberId;
    private Long couponRuleId;
    private Integer couponCount;
    private BigDecimal finalPayAmount;
    private BigDecimal memberCouponDeduct;
    private NormalizedPaymentResult paymentResult;
    private String orderNo;
}
