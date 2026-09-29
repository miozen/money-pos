package com.money.dto.pos;

import lombok.Data;
import javax.validation.constraints.NotEmpty;

@Data
public class MemberAmountPickupRefundDTO { @NotEmpty private String pickupNo; @NotEmpty private String reqId; }
