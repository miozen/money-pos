package com.money.feature.trade.application.boundary.facade.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SaleStockLine {
    private Long goodsId;
    private String goodsName;
    private String goodsBarcode;
    private Integer quantity;
    private BigDecimal purchasePrice;
    private Boolean combo;
}
