package com.money.feature.trade.application.orderquery;

import com.money.dto.OmsOrder.OrderCountVO;

import java.time.LocalDateTime;

/**
 * TRADE-owned compatibility read service for the legacy OMS statistics endpoint.
 */
public interface OmsOrderStatisticsQueryService {

    OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime);
}
