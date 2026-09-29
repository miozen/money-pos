package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberAmountPickupVO { private String pickupNo; private String supplementReceiptNo; private BigDecimal rightDeductAmount; private BigDecimal supplementAmount; }
