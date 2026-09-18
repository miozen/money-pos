package com.money.contract.goods;

import java.time.LocalDateTime;
import java.util.List;

/** GMS-owned daily inbound-procurement inputs for FIN's waterfall report. */
public interface FinanceWaterfallInventoryQuery {

    List<FinanceWaterfallProcurementSnapshot> listDailyInboundProcurements(LocalDateTime startInclusive,
                                                                            LocalDateTime endInclusive);
}
