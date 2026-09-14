package com.money.feature.trade.application.boundary.facade.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MemberAssetRefundRequest {
    private Long memberId;
    private BigDecimal salesAmount;
    private BigDecimal memberCouponAmount;
    private boolean increaseCancelTimes;
    private String orderNo;
    private boolean restoreVouchers;
    private List<BalancePayment> balancePayments;

    @Data
    public static class BalancePayment {
        private String payMethodCode;
        private BigDecimal payAmount;
    }
}
