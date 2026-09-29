package com.money.contract.member;

import lombok.Data;

import java.util.List;

/** UMS-owned command for consuming deferred quantity rights at physical pickup. */
@Data
public class MemberQuantityPickupCommand {
    private Long memberId;
    private String pickupNo;
    private String requestNo;
    private String operatorName;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long rightId;
        private Integer quantity;
    }
}
