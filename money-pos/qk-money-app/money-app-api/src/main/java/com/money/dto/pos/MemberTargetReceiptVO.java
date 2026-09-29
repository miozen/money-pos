package com.money.dto.pos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberTargetReceiptVO { private String receiptNo; private BigDecimal amount; }
