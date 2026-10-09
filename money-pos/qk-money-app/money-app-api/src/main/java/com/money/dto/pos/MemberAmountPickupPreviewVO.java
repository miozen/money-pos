package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** Non-persistent pricing snapshot for one selected AMOUNT right. */
@Data
public class MemberAmountPickupPreviewVO {
    private List<Line> lines;
    private BigDecimal goodsAmount;
    private BigDecimal rightDeductAmount;
    private BigDecimal supplementAmount;
    private BigDecimal remainingAmountAfter;
    @Data public static class Line { private Long goodsId; private String goodsName; private Integer quantity; private BigDecimal unitPrice; private BigDecimal amount; }
}
