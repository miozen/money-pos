package com.money.contract.member;

import lombok.Data;
import java.math.BigDecimal;

/** UMS-owned write commands for member brand benefit ledgers. No TRADE/GMS implementation leaks through this contract. */
public final class MemberBrandBenefitLedgerCommand {
    private MemberBrandBenefitLedgerCommand() { }

    @Data
    public static class QuantityGrant {
        private Long memberId; private String brandId; private Long goodsId;
        private String sourceOrderNo; private Long sourceOrderDetailId; private Integer quantity;
        private String requestNo; private String operatorName; private String reason;
    }
    @Data
    public static class QuantityChange {
        private Long rightId; private Integer delta; private Integer pickedDelta;
        private String action; private String requestNo; private String sourceType; private String sourceNo;
        private String operatorName; private String reason;
    }
    @Data
    public static class AmountGrant {
        private Long memberId; private String brandId; private String tierCode; private String sourceReceiptNo;
        private BigDecimal amount; private String requestNo; private String operatorName; private String reason;
    }
    @Data
    public static class AmountChange {
        private Long rightId; private BigDecimal delta; private String action; private String requestNo;
        private String sourceType; private String sourceNo; private String operatorName; private String reason;
    }
    @Data
    public static class TargetPlanCreate {
        private Long memberId; private String brandId; private String currentLevelCode;
        private String targetTierCode; private BigDecimal initialProgress; private String requestNo;
        private String operatorName; private String sourceType; private String sourceNo; private String reason;
    }
    @Data
    public static class TargetProgressChange {
        private Long planId; private BigDecimal delta; private String action; private String requestNo;
        private String sourceType; private String sourceNo; private String operatorName; private String reason;
    }
}
