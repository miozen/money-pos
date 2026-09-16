package com.money.contract.member;

import java.util.Map;
import java.math.BigDecimal;

/** 订单详情展示所需的会员档案快照。 */
public class MemberOrderProfileSnapshot {

    private final Long id;
    private final String name;
    private final String phone;
    private final BigDecimal coupon;
    private final Map<String, String> brandLevels;
    private final Map<String, String> brandLevelDesc;

    public MemberOrderProfileSnapshot(Long id, String name, String phone, BigDecimal coupon,
                                      Map<String, String> brandLevels,
                                      Map<String, String> brandLevelDesc) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.coupon = coupon;
        this.brandLevels = brandLevels;
        this.brandLevelDesc = brandLevelDesc;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public BigDecimal getCoupon() { return coupon; }
    public Map<String, String> getBrandLevels() { return brandLevels; }
    public Map<String, String> getBrandLevelDesc() { return brandLevelDesc; }
}
