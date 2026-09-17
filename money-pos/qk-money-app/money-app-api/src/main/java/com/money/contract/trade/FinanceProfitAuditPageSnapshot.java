package com.money.contract.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable page metadata and TRADE profit-audit rows. */
public final class FinanceProfitAuditPageSnapshot {
    private final long current;
    private final long size;
    private final long total;
    private final List<FinanceProfitAuditSnapshot> records;

    public FinanceProfitAuditPageSnapshot(long current, long size, long total,
                                          List<FinanceProfitAuditSnapshot> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = Collections.unmodifiableList(new ArrayList<FinanceProfitAuditSnapshot>(records));
    }

    public long getCurrent() { return current; }
    public long getSize() { return size; }
    public long getTotal() { return total; }
    public List<FinanceProfitAuditSnapshot> getRecords() { return records; }
}
