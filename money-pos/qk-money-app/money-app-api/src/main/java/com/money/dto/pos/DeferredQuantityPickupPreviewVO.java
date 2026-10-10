package com.money.dto.pos;

import lombok.Data;
import java.util.List;

/** Read-only result for the deferred-quantity pickup confirmation screen. */
@Data
public class DeferredQuantityPickupPreviewVO {
    private List<Line> lines;

    @Data public static class Line {
        private Long rightId;
        private Long goodsId;
        private String goodsName;
        private Integer grantedQuantity;
        private Integer pickedQuantity;
        private Integer pickupQuantity;
        private Integer remainingQuantity;
        private Integer remainingAfterPickup;
    }
}
