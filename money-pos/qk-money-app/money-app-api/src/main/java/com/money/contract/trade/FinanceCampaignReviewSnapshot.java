package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceCampaignReviewSnapshot {
    private final String ruleName;
    private final int usedCount;
    private final BigDecimal totalDiscount;
    private final BigDecimal totalRevenue;
    public FinanceCampaignReviewSnapshot(String ruleName, int usedCount, BigDecimal totalDiscount, BigDecimal totalRevenue) {
        this.ruleName = ruleName; this.usedCount = usedCount; this.totalDiscount = totalDiscount; this.totalRevenue = totalRevenue;
    }
    public String getRuleName() { return ruleName; }
    public int getUsedCount() { return usedCount; }
    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
}
