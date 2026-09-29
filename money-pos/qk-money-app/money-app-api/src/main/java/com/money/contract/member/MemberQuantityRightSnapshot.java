package com.money.contract.member;

import lombok.Data;

/** Entity-free quantity-right snapshot. */
@Data
public class MemberQuantityRightSnapshot {
    private Long rightId;
    private Long memberId;
    private String brandId;
    private Long goodsId;
    private String sourceOrderNo;
    private Long sourceOrderDetailId;
    private Integer remainingQuantity;
}
