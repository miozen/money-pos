package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class MemberAmountPickupDTO {
    @NotNull private Long memberId;
    @NotNull private Long amountRightId;
    @NotEmpty private String reqId;
    @NotEmpty private List<Line> lines;
    private List<SettleAccountsDTO.PaymentItem> payments;
    @Data public static class Line { @NotNull private Long goodsId; @NotNull private Integer quantity; }
}
