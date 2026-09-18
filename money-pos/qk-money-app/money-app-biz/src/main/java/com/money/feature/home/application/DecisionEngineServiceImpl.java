package com.money.feature.home.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/** Compatibility facade that preserves the existing GET-triggered refresh flow. */
@Service
@RequiredArgsConstructor
public class DecisionEngineServiceImpl implements DecisionEngineService {
    private final HomeDailySnapshotCommandService dailySnapshotCommandService;
    private final HomeDashboardQueryService dashboardQueryService;

    @Override
    public void compensateSnapshots(int daysToCheck) {
        dailySnapshotCommandService.compensateMissingSnapshots(daysToCheck);
    }

    @Override
    public void generateDailySnapshot(LocalDate date) {
        dailySnapshotCommandService.refreshSnapshot(date);
    }

    @Override
    public Map<String, Object> getTodayDashboardWithAlerts() {
        refreshTodayForCompatibility();
        return dashboardQueryService.getTodayDashboardWithAlerts();
    }

    @Override
    public Map<String, Object> getComprehensiveDashboard() {
        refreshTodayForCompatibility();
        return dashboardQueryService.getComprehensiveDashboard();
    }

    private void refreshTodayForCompatibility() {
        compensateSnapshots(7);
        generateDailySnapshot(LocalDate.now());
    }
}
