package com.money.contract.goods;

import java.math.BigDecimal;

/** GMS-owned refund stock write boundary. */
public interface RefundStockCommandHandler {
    BigDecimal handle(RefundStockCommand command);
}
