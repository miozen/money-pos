package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class DeferredQuantityPickupDTO {
    @NotNull private Long memberId;
    @NotEmpty private String reqId;
    @NotEmpty private List<Line> lines;
    @Data public static class Line { @NotNull private Long rightId; @NotNull private Integer quantity; }
}
