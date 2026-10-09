package com.money.dto.memberbenefit;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** POS request for creating a UMS-owned TARGET plan. */
@Data
public class MemberTargetPlanCreateDTO {
    @NotNull private Long memberId;
    @NotBlank private String brandId;
    @NotBlank private String targetTierCode;
    private BigDecimal initialProgress;
    @NotBlank private String reqId;
    private String reason;
}
