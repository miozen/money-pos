package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/** Read-only amount-right pickup pricing request; final pickup revalidates every value. */
@Data
public class MemberAmountPickupPreviewDTO {
    @NotNull private Long memberId;
    @NotNull private Long amountRightId;
    @NotEmpty private List<MemberAmountPickupDTO.Line> lines;
}
