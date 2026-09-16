package com.money.contract.member;

/**
 * 收银流程所需的已核验会员快照。
 *
 * <p>该对象刻意只包含订单归档与会员身份确认所需的字段，
 * 不暴露余额、等级、积分或 UMS 持久化实体。</p>
 */
public class MemberCheckoutSnapshot {

    private final Long memberId;
    private final String name;
    private final String phone;

    public MemberCheckoutSnapshot(Long memberId, String name, String phone) {
        this.memberId = memberId;
        this.name = name;
        this.phone = phone;
    }

    public Long getMemberId() { return memberId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
}
