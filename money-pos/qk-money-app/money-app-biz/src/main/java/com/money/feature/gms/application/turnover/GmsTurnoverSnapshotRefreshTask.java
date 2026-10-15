package com.money.feature.gms.application.turnover;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Refreshes the GMS turnover read model without giving HTTP GET endpoints write authority. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmsTurnoverSnapshotRefreshTask {
    private static final long FIVE_MINUTES_MILLIS = 5 * 60 * 1000L;

    private final GmsTurnoverSnapshotCommandService snapshotCommandService;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void refreshTodayOnStartup() {
        refreshSnapshot("application startup");
    }

    @Scheduled(fixedDelay = FIVE_MINUTES_MILLIS, initialDelay = FIVE_MINUTES_MILLIS)
    public void refreshSnapshotOnSchedule() {
        refreshSnapshot("scheduled refresh");
    }

    void refreshSnapshot(String trigger) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.warn("Skipping GMS turnover snapshot {} because another refresh is still running", trigger);
            return;
        }
        try {
            snapshotCommandService.refreshTodaySnapshot();
            log.info("Completed GMS turnover snapshot {}", trigger);
        } catch (Exception ex) {
            log.error("GMS turnover snapshot {} failed; retaining the last valid snapshot", trigger, ex);
        } finally {
            refreshInProgress.set(false);
        }
    }
}
