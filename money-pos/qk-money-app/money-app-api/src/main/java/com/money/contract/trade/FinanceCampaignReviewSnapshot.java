package com.money.contract.trade;

import java.math.BigDecimal;

public class FinanceCampaignReviewSnapshot {
    private final String ruleName;
    private final String ruleType;
    private final int usedCount;
    private final BigDecimal totalDiscount;
    private final BigDecimal totalRevenue;
    public FinanceCampaignReviewSnapshot(String ruleName, int usedCount, BigDecimal totalDiscount, BigDecimal totalRevenue) {
        this(ruleName, null, usedCount, totalDiscount, totalRevenue);
    }
    public FinanceCampaignReviewSnapshot(String ruleName, String ruleType, int usedCount,
                                         BigDecimal totalDiscount, BigDecimal totalRevenue) {
        this.ruleName = ruleName; this.ruleType = ruleType; this.usedCount = usedCount;
        this.totalDiscount = totalDiscount; this.totalRevenue = totalRevenue;
    }
    public String getRuleName() { return ruleName; }
    public String getRuleType() { return ruleType; }
    public int getUsedCount() { return usedCount; }
    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
}
