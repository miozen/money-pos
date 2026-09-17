package com.money.contract.goods;

import java.math.BigDecimal;

/** Read-only current inventory valuation owned by GMS. */
public interface InventoryValuationQuery {

    BigDecimal getCurrentStockValue();
}
