package com.money.feature.trade.application.boundary.facade.dto;

import lombok.Data;
import java.util.List;

@Data
public class RefundStockRequest {
    private String orderNo;
    private List<RefundStockLine> lines;
}
