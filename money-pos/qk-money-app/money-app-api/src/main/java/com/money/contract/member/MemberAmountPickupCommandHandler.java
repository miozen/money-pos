package com.money.contract.member;

/** UMS-owned AMOUNT-right pickup write boundary. */
public interface MemberAmountPickupCommandHandler {
    void handle(MemberAmountPickupCommand command);
}
