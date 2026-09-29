package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class MemberAmountPackagePurchaseDTO {
    @NotNull private Long memberId;
    @NotEmpty private String brandId;
    @NotEmpty private String tierCode;
    @NotEmpty private String reqId;
    @NotEmpty private List<SettleAccountsDTO.PaymentItem> payments;
}
