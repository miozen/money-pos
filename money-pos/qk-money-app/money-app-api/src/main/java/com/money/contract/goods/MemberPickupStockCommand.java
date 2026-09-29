package com.money.contract.goods;

import lombok.Data;

import java.util.List;

/** TRADE requests GMS to deduct physical stock when a member collects a deferred quantity right. */
@Data
public class MemberPickupStockCommand {
    private String pickupNo;
    private List<StockMutationLine> lines;
}
