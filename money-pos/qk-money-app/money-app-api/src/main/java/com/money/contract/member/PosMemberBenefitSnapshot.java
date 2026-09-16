package com.money.contract.member;

import java.util.List;

/** One member's usable voucher count and rule summaries for POS display. */
public class PosMemberBenefitSnapshot {

    private final int voucherCount;
    private final List<PosCouponRuleSnapshot> couponRules;

    public PosMemberBenefitSnapshot(int voucherCount, List<PosCouponRuleSnapshot> couponRules) {
        this.voucherCount = voucherCount;
        this.couponRules = couponRules;
    }

    public int getVoucherCount() {
        return voucherCount;
    }

    public List<PosCouponRuleSnapshot> getCouponRules() {
        return couponRules;
    }
}
