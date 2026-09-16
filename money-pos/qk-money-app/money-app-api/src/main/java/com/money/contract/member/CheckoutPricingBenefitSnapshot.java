package com.money.contract.member;

import java.math.BigDecimal;
import java.util.Map;

/** Read-only membership levels and voucher rule data used by TRADE pricing. */
public class CheckoutPricingBenefitSnapshot {

    private final Map<String, String> memberBrandLevels;
    private final VoucherRule voucherRule;

    public CheckoutPricingBenefitSnapshot(Map<String, String> memberBrandLevels, VoucherRule voucherRule) {
        this.memberBrandLevels = memberBrandLevels;
        this.voucherRule = voucherRule;
    }

    public Map<String, String> getMemberBrandLevels() {
        return memberBrandLevels;
    }

    public VoucherRule getVoucherRule() {
        return voucherRule;
    }

    public static class VoucherRule {
        private final BigDecimal thresholdAmount;
        private final BigDecimal discountAmount;

        public VoucherRule(BigDecimal thresholdAmount, BigDecimal discountAmount) {
            this.thresholdAmount = thresholdAmount;
            this.discountAmount = discountAmount;
        }

        public BigDecimal getThresholdAmount() {
            return thresholdAmount;
        }

        public BigDecimal getDiscountAmount() {
            return discountAmount;
        }
    }
}
