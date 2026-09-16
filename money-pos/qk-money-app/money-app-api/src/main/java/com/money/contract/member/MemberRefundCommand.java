package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** 会员退款资产写入命令。 */
@Data
public class MemberRefundCommand {
    private Long memberId;
    private String orderNo;
    private BigDecimal salesAmount;
    private BigDecimal memberCouponRefund;
    private boolean increaseCancelTimes;
    private boolean restoreVouchers;
    private BigDecimal balanceRefundAmount;
}
