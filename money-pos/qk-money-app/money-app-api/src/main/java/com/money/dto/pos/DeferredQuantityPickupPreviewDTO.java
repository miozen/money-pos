package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/** Read-only draft used to preview a deferred-quantity pickup before confirmation. */
@Data
public class DeferredQuantityPickupPreviewDTO {
    @NotNull private Long memberId;
    @NotEmpty private List<Line> lines;

    @Data public static class Line {
        @NotNull private Long rightId;
        @NotNull private Integer quantity;
    }
}
