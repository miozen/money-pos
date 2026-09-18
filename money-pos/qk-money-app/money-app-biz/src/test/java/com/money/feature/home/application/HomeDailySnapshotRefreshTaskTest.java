package com.money.feature.home.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HomeDailySnapshotRefreshTaskTest {

    @Test
    void skipsOverlappingRefreshes() throws Exception {
        CountDownLatch firstRefreshStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRefresh = new CountDownLatch(1);
        AtomicInteger commandCalls = new AtomicInteger();
        HomeDailySnapshotRefreshTask task = new HomeDailySnapshotRefreshTask(new HomeDailySnapshotCommandService() {
            @Override
            public void compensateMissingSnapshots(int daysToCheck) {
                commandCalls.incrementAndGet();
                firstRefreshStarted.countDown();
                await(releaseFirstRefresh);
            }

            @Override
            public void refreshSnapshot(LocalDate date) {
                commandCalls.incrementAndGet();
            }
        });

        Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                task.refreshSnapshots("first refresh");
            }
        });
        first.start();
        assertThat(firstRefreshStarted.await(2, TimeUnit.SECONDS)).isTrue();
        task.refreshSnapshots("overlapping refresh");
        releaseFirstRefresh.countDown();
        first.join(2000);

        assertThat(commandCalls.get()).isEqualTo(2);
    }

    @Test
    void releasesTheGuardAfterFailureSoTheNextRefreshCanRun() {
        AtomicInteger refreshAttempts = new AtomicInteger();
        HomeDailySnapshotRefreshTask task = new HomeDailySnapshotRefreshTask(new HomeDailySnapshotCommandService() {
            @Override
            public void compensateMissingSnapshots(int daysToCheck) {
                // No-op: the writer is not reached when the assembly command fails.
            }

            @Override
            public void refreshSnapshot(LocalDate date) {
                if (refreshAttempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("simulated refresh failure");
                }
            }
        });

        task.refreshSnapshots("failing refresh");
        task.refreshSnapshots("retry refresh");

        assertThat(refreshAttempts.get()).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
