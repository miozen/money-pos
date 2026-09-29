package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** Entity-free AMOUNT right snapshot used by TRADE pickup orchestration. */
@Data
public class MemberAmountRightSnapshot {
    private Long rightId;
    private Long memberId;
    private String brandId;
    private String tierCodeSnapshot;
    private String tierNameSnapshot;
    private String pricingLevelCodeSnapshot;
    private BigDecimal remainingAmount;
    private String sourceReceiptNo;
}
