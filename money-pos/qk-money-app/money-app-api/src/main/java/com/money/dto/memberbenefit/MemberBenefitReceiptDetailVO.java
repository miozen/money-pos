package com.money.dto.memberbenefit;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only snapshot of a non-order member-benefit receipt owned by TRADE. */
@Data
public class MemberBenefitReceiptDetailVO {
    private String receiptNo;
    private String receiptKind;
    private String receiptType;
    private Long memberId;
    private String brandId;
    private Long rightOrPlanId;
    private BigDecimal totalAmount;
    private BigDecimal rightDeductAmount;
    private BigDecimal supplementAmount;
    private String status;
    private String reason;
    private LocalDateTime createTime;
    private List<Payment> payments;

    @Data
    public static class Payment {
        private String payMethodCode;
        private String payMethodName;
        private String payTag;
        private BigDecimal amount;
    }
}
