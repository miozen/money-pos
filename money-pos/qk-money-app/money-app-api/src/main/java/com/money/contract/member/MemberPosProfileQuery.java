package com.money.contract.member;

import java.util.List;

/** 为 POS 会员搜索提供权益快照的中立只读契约。 */
public interface MemberPosProfileQuery {

    List<MemberPosProfileSnapshot> searchActiveMembers(String keyword);
}
