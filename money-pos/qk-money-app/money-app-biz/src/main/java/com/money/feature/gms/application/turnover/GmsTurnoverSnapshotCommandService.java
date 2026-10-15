package com.money.feature.gms.application.turnover;

/** GMS-owned write command for the turnover-warning daily read model. */
public interface GmsTurnoverSnapshotCommandService {
    void refreshTodaySnapshot();
}
