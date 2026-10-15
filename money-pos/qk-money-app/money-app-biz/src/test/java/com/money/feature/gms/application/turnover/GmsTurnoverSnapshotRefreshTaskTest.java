package com.money.feature.gms.application.turnover;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GmsTurnoverSnapshotRefreshTaskTest {

    @Test
    void skipsOverlappingRefreshes() throws Exception {
        CountDownLatch firstRefreshStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRefresh = new CountDownLatch(1);
        AtomicInteger commandCalls = new AtomicInteger();
        GmsTurnoverSnapshotRefreshTask task = new GmsTurnoverSnapshotRefreshTask(() -> {
            commandCalls.incrementAndGet();
            firstRefreshStarted.countDown();
            await(releaseFirstRefresh);
        });

        Thread first = new Thread(() -> task.refreshSnapshot("first refresh"));
        first.start();
        assertThat(firstRefreshStarted.await(2, TimeUnit.SECONDS)).isTrue();
        task.refreshSnapshot("overlapping refresh");
        releaseFirstRefresh.countDown();
        first.join(2000);

        assertThat(commandCalls.get()).isEqualTo(1);
    }

    @Test
    void releasesTheGuardAfterFailureSoTheNextRefreshCanRun() {
        AtomicInteger refreshAttempts = new AtomicInteger();
        GmsTurnoverSnapshotRefreshTask task = new GmsTurnoverSnapshotRefreshTask(() -> {
            if (refreshAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException("simulated refresh failure");
            }
        });

        task.refreshSnapshot("failing refresh");
        task.refreshSnapshot("retry refresh");

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
