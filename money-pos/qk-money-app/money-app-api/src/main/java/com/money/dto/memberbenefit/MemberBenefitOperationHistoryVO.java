package com.money.dto.memberbenefit;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Bounded, UMS-owned operation history for one member-benefit workspace entry. */
@Data
public class MemberBenefitOperationHistoryVO {
    private List<Record> records;

    @Data
    public static class Record {
        private String benefitType;
        private Long subjectId;
        private String subjectName;
        private String action;
        private String actionName;
        private BigDecimal delta;
        private BigDecimal afterValue;
        private String unit;
        private String referenceNo;
        private String detailTarget;
        private String operatorName;
        private String status;
        private String reason;
        private LocalDateTime createTime;
    }
}
