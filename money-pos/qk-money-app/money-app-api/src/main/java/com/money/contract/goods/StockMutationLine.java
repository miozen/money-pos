package com.money.contract.goods;

import lombok.Data;

import java.math.BigDecimal;

/** Inventory mutation input without GMS persistence entities. */
@Data
public class StockMutationLine {
    private Long goodsId;
    private String goodsName;
    private String goodsBarcode;
    private Integer quantity;
    private BigDecimal purchasePrice;
    private Boolean combo;
}
