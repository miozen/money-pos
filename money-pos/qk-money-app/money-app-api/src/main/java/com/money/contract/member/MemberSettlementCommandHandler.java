package com.money.contract.member;

/** UMS 所拥有的会员结算资产写入契约。 */
public interface MemberSettlementCommandHandler {
    void handle(MemberSettlementCommand command);
}
