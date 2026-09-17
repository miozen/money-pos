package com.money.contract.trade;

import java.time.LocalDateTime;

/** TRADE-owned order aggregate for the legacy HOME count service. */
public interface HomeOrderReadQuery {

    /**
     * Uses HOME's established financial-status set and a right-open time range.
     * A null boundary means unbounded on that side.
     */
    HomeOrderReadSnapshot summarizeHomeCount(LocalDateTime startInclusive, LocalDateTime endExclusive);
}
