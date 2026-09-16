package com.money.contract.member;

/** UMS 所拥有的会员退款资产写入契约。 */
public interface MemberRefundCommandHandler {
    void handle(MemberRefundCommand command);
}
