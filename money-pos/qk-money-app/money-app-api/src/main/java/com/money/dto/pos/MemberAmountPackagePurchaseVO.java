package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberAmountPackagePurchaseVO { private String receiptNo; private Long amountRightId; private BigDecimal amount; }
