package com.money.feature.trade.application.boundary.facade.dto;

import lombok.Data;
import java.util.List;

@Data
public class SaleStockRequest {
    private String orderNo;
    private List<SaleStockLine> lines;
}
