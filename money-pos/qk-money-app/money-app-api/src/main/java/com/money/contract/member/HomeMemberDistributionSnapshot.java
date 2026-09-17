package com.money.contract.member;

/** Current active-member count for one brand and membership level on the HOME chart. */
public class HomeMemberDistributionSnapshot {

    private final String brandName;
    private final String levelCode;
    private final int count;

    public HomeMemberDistributionSnapshot(String brandName, String levelCode, int count) {
        this.brandName = brandName;
        this.levelCode = levelCode;
        this.count = count;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public int getCount() {
        return count;
    }
}
