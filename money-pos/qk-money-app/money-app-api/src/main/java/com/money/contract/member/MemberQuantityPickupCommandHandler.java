package com.money.contract.member;

/** UMS-owned write boundary; callers cannot mutate quantity-right balances directly. */
public interface MemberQuantityPickupCommandHandler {
    void handle(MemberQuantityPickupCommand command);
}
