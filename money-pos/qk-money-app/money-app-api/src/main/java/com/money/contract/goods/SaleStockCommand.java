package com.money.contract.goods;

import lombok.Data;

import java.util.List;

/** TRADE requests GMS to deduct stock for a completed sale. */
@Data
public class SaleStockCommand {
    private String orderNo;
    private List<StockMutationLine> lines;
}
