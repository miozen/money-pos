package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MemberTargetAdjustmentDTO {
    @NotNull private Long targetPlanId;
    @NotEmpty private String reqId;
    @NotNull private BigDecimal amount;
    private String reason;
    private List<SettleAccountsDTO.PaymentItem> payments;
}
