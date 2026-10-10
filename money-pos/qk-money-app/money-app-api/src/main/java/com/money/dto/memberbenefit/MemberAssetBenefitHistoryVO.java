package com.money.dto.memberbenefit;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/** Read-only mixed-unit member asset and benefit audit view. */
@Data public class MemberAssetBenefitHistoryVO { private List<Record> records;
 @Data public static class Record { private LocalDateTime createTime; private String businessType; private String dimension; private BigDecimal delta; private BigDecimal beforeValue; private BigDecimal afterValue; private String unit; private String referenceNo; private String documentType; private String detailTarget; private String operatorName; private String status; private String summary; }
}
