package com.money.feature.home.application;

import java.time.LocalDate;

/** HOME-owned commands for compensation and refresh of its daily read model. */
public interface HomeDailySnapshotCommandService {
    void compensateMissingSnapshots(int daysToCheck);
    void refreshSnapshot(LocalDate date);
}
