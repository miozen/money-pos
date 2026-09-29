package com.money.contract.member;

import lombok.Data;

/** UMS-owned allocation result; TRADE uses pickedReturnQuantity only for a physical GMS return. */
@Data
public class MemberQuantityRefundResult {
    private Long sourceOrderDetailId;
    private Long goodsId;
    private boolean deferredQuantity;
    private Integer cancelledUnpickedQuantity;
    private Integer pickedReturnQuantity;
}
