package com.money.contract.member;

/** 为收银核验提供会员快照的中立只读契约。 */
public interface MemberCheckoutQuery {

    /** 查询可参与收银的会员；不存在或已删除时返回 {@code null}。 */
    MemberCheckoutSnapshot findActiveMemberById(Long memberId);
}
