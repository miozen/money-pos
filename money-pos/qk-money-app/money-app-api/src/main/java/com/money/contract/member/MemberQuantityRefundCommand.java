package com.money.contract.member;

import lombok.Data;

import java.util.List;

/** TRADE asks UMS to split a QUANTITY refund into unpicked cancellation and picked physical return. */
@Data
public class MemberQuantityRefundCommand {
    private String requestNo;
    private String sourceOrderNo;
    private String operatorName;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long sourceOrderDetailId;
        private Integer quantity;
    }
}
