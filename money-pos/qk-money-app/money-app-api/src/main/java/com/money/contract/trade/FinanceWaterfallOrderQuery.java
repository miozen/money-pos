package com.money.contract.trade;

import java.time.LocalDateTime;
import java.util.List;

/** TRADE-owned daily order inputs for FIN's waterfall report. */
public interface FinanceWaterfallOrderQuery {

    List<FinanceWaterfallOrderSnapshot> listDailyWaterfallOrders(LocalDateTime startInclusive,
                                                                  LocalDateTime endInclusive);
}
