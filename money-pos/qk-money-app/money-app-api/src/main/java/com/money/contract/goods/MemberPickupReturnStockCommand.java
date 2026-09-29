package com.money.contract.goods;

import lombok.Data;

import java.util.List;

/** TRADE requests GMS to restore only physical goods previously collected from quantity rights. */
@Data
public class MemberPickupReturnStockCommand {
    private String returnNo;
    private List<StockMutationLine> lines;
}
