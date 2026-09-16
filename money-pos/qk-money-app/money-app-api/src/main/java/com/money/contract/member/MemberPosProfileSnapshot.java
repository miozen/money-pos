package com.money.contract.member;

import java.math.BigDecimal;
import java.util.Map;

/** POS 会员搜索及定价所需的权益快照。 */
public class MemberPosProfileSnapshot {

    private final Long id;
    private final String code;
    private final String name;
    private final String type;
    private final String phone;
    private final BigDecimal coupon;
    private final BigDecimal balance;
    private final Long levelId;
    private final Map<String, String> brandLevels;

    public MemberPosProfileSnapshot(Long id, String code, String name, String type, String phone,
                                    BigDecimal coupon, BigDecimal balance, Long levelId,
                                    Map<String, String> brandLevels) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.phone = phone;
        this.coupon = coupon;
        this.balance = balance;
        this.levelId = levelId;
        this.brandLevels = brandLevels;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getPhone() { return phone; }
    public BigDecimal getCoupon() { return coupon; }
    public BigDecimal getBalance() { return balance; }
    public Long getLevelId() { return levelId; }
    public Map<String, String> getBrandLevels() { return brandLevels; }
}
