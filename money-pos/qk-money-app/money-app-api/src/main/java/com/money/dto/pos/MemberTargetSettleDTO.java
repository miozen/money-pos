package com.money.dto.pos;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
public class MemberTargetSettleDTO {
    @NotNull private Long targetPlanId;
    @NotNull @Valid private SettleAccountsDTO settle;
}
