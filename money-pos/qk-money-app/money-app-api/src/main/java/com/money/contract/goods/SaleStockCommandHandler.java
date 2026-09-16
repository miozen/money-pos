package com.money.contract.goods;

/** GMS-owned sale stock write boundary. */
public interface SaleStockCommandHandler {
    void handle(SaleStockCommand command);
}
