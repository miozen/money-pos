package com.money.feature.home.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/** Refreshes the HOME daily read model without giving HTTP GET endpoints write authority. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeDailySnapshotRefreshTask {
    private static final long FIVE_MINUTES_MILLIS = 5 * 60 * 1000L;

    private final HomeDailySnapshotCommandService dailySnapshotCommandService;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void refreshTodayOnStartup() {
        refreshSnapshots("application startup");
    }

    @Scheduled(fixedDelay = FIVE_MINUTES_MILLIS, initialDelay = FIVE_MINUTES_MILLIS)
    public void refreshSnapshotsOnSchedule() {
        refreshSnapshots("scheduled refresh");
    }

    void refreshSnapshots(String trigger) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.warn("Skipping HOME daily snapshot {} because another refresh is still running", trigger);
            return;
        }
        try {
            dailySnapshotCommandService.compensateMissingSnapshots(7);
            dailySnapshotCommandService.refreshSnapshot(LocalDate.now());
            log.info("Completed HOME daily snapshot {}", trigger);
        } catch (Exception ex) {
            // Assembly happens before the single-statement writer; a failed refresh leaves the prior row intact.
            log.error("HOME daily snapshot {} failed; retaining the last valid snapshot", trigger, ex);
        } finally {
            refreshInProgress.set(false);
        }
    }
}
