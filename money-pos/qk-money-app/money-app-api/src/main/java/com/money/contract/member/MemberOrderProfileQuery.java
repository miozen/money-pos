package com.money.contract.member;

/** 为订单详情展示提供会员档案快照的中立只读契约。 */
public interface MemberOrderProfileQuery {

    /** 查询会员档案；关联会员已不存在时返回 {@code null}。 */
    MemberOrderProfileSnapshot findByMemberId(Long memberId);
}
