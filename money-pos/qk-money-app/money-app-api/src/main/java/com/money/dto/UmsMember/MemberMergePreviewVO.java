package com.money.dto.UmsMember;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** Read-only eligibility snapshot; it does not reserve or mutate either member. */
@Data
public class MemberMergePreviewVO {
    private MemberSummary source;
    private MemberSummary target;
    private long sourceOrderCount;
    private long sourceUnusedCouponCount;
    /** Amount-style coupon credit is deliberately not transferred in the first merge version. */
    private boolean sourceHasCouponCredit;
    private boolean mergeAllowed;
    private List<String> blockers;

    @Data
    public static class MemberSummary {
        private Long id;
        private String name;
        private String phone;
        private BigDecimal balance;
        private BigDecimal coupon;
    }
}
