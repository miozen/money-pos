package com.money.contract.goods;

import java.math.BigDecimal;

public interface MemberPickupReturnStockCommandHandler {
    BigDecimal handle(MemberPickupReturnStockCommand command);
}
