package com.money.dto.pos;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** Non-sales receipt data for one completed deferred-quantity pickup. */
@Data
public class MemberQuantityPickupReceiptVO {
    private String pickupNo;
    private Long memberId;
    private String operatorName;
    private LocalDateTime pickupTime;
    private List<Line> lines;

    @Data public static class Line { private String goodsName; private String goodsBarcode; private Integer quantity; }
}
