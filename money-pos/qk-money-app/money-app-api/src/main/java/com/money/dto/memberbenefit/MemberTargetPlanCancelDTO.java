package com.money.dto.memberbenefit;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** Audited cancellation request; TARGET plans are never physically deleted. */
@Data
public class MemberTargetPlanCancelDTO {
    @NotNull private Long planId;
    @NotBlank private String reqId;
    @NotBlank private String reason;
}
