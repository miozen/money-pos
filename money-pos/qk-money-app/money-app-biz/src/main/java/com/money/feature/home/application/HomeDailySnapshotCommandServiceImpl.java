package com.money.feature.home.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Keeps the legacy seven-day compensation and same-day refresh triggers as explicit commands. */
@Service
@RequiredArgsConstructor
public class HomeDailySnapshotCommandServiceImpl implements HomeDailySnapshotCommandService {
    private final HomeDailySummaryQueryService dailySummaryQueryService;
    private final HomeDailySnapshotAssembler snapshotAssembler;
    private final HomeDailySummaryWriter dailySummaryWriter;

    @Override
    public void compensateMissingSnapshots(int daysToCheck) {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= daysToCheck; i++) {
            LocalDate targetDate = today.minusDays(i);
            if (!dailySummaryQueryService.exists(targetDate)) {
                dailySummaryWriter.insertIfAbsent(snapshotAssembler.assemble(targetDate));
            }
        }
    }

    @Override
    public void refreshSnapshot(LocalDate date) {
        dailySummaryWriter.refresh(snapshotAssembler.assemble(date));
    }
}
