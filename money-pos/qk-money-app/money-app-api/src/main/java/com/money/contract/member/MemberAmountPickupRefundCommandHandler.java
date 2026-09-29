package com.money.contract.member;

/** UMS-owned AMOUNT-right refund write boundary. */
public interface MemberAmountPickupRefundCommandHandler {
    void handle(MemberAmountPickupRefundCommand command);
}
