package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class MemberTargetConfirmDTO {
    @NotNull private Long targetPlanId;
    @NotEmpty private String reqId;
    private String reason;
}
