package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** UMS-owned reversal of a fully refunded AMOUNT pickup. */
@Data
public class MemberAmountPickupRefundCommand {
    private Long memberId;
    private Long rightId;
    private String pickupNo;
    private String refundNo;
    private String requestNo;
    private BigDecimal amount;
    private String operatorName;
}
