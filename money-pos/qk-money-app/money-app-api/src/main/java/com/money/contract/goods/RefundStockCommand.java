package com.money.contract.goods;

import lombok.Data;

import java.util.List;

/** TRADE requests GMS to restore stock for a refund. */
@Data
public class RefundStockCommand {
    private String orderNo;
    private List<StockMutationLine> lines;
}
