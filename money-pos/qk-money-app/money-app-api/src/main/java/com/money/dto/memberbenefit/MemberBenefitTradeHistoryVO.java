package com.money.dto.memberbenefit;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned non-product receipt and pickup history. */
@Data
public class MemberBenefitTradeHistoryVO {
    private List<Record> records;

    @Data public static class Record {
        private String recordType; private String referenceNo; private String status; private BigDecimal amount;
        private String sourceNo; private String refundNo; private LocalDateTime createTime;
    }
}
