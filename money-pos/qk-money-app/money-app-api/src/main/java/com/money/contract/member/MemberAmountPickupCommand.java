package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** UMS-owned AMOUNT-right movement for one completed physical pickup. */
@Data
public class MemberAmountPickupCommand {
    private Long memberId;
    private Long rightId;
    private String pickupNo;
    private String requestNo;
    private BigDecimal amount;
    private String operatorName;
}
