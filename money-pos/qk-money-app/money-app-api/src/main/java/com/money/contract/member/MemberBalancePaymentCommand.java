package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** Balance-only payment movement for non-order business receipts. */
@Data
public class MemberBalancePaymentCommand {
    private Long memberId; private String receiptNo; private String requestNo; private BigDecimal amount; private boolean refund;
}
