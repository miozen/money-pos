package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** 会员结算资产写入命令。 */
@Data
public class MemberSettlementCommand {
    private Long memberId;
    private String orderNo;
    private BigDecimal finalPayAmount;
    private BigDecimal memberCouponDeduct;
    private Long voucherRuleId;
    private Integer voucherCount;
    private boolean balancePaymentRequested;
    private BigDecimal balancePaymentAmount;
}
