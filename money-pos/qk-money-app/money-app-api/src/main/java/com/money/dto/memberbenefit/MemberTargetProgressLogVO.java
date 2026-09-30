package com.money.dto.memberbenefit;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MemberTargetProgressLogVO {
    private Long id; private String action; private BigDecimal amountDelta; private BigDecimal beforeAmount; private BigDecimal afterAmount;
    private String requestNo; private String sourceType; private String sourceNo; private String operatorName; private String reason;
    private LocalDateTime createTime;
}
