package com.money.contract.goods;

/** GMS-owned physical-stock write boundary for member deferred pickups. */
public interface MemberPickupStockCommandHandler {
    void handle(MemberPickupStockCommand command);
}
