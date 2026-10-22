package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local readiness loop only. It is disabled until a separately authorized signed Worker publisher is provided.
 * Enabling this property without such a publisher must remain impossible.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberCloudSyncDispatcher {
    private final MemberCloudSyncOutboxService outboxService;
    private final MemberCloudInitialBackfillService initialBackfillService;
    private final MemberCloudSnapshotAssembler snapshotAssembler;
    private final MemberCloudSyncProperties properties;
    private final HttpsMemberCloudSyncPublisher publisher;

    @Scheduled(fixedDelayString = "${omnia.cloud.sync.fixed-delay-ms:60000}")
    public void dispatchWhenPublisherIsInstalled() {
        dispatchNow();
    }

    /** Allows the local administrator page to run the same bounded, safe dispatch loop on demand. */
    public void dispatchNow() {
        if (!properties.isEnabled()) return;
        int recovered = outboxService.recoverExpiredProcessing();
        if (recovered > 0) log.warn("Omnia Cloud恢复了中断的待发送任务，count={}", recovered);
        int backfilled = initialBackfillService.enqueueNextChunk();
        if (backfilled > 0) log.info("Omnia Cloud首次会员回填已入待发箱，count={}", backfilled);
        List<CloudMemberSyncOutbox> claimed = outboxService.claimDue(50);
        if (claimed.isEmpty()) return;
        try {
            List<MemberCloudSyncRequestFactory.CloudSyncItem> items = claimed.stream().map(item ->
                    new MemberCloudSyncRequestFactory.CloudSyncItem(item, "UPSERT".equals(item.getOperation()) ? snapshotAssembler.assemble(item.getMemberId()) : null))
                    .collect(java.util.stream.Collectors.toList());
            publisher.publish(items);
            claimed.forEach(outboxService::acknowledge);
        } catch (Exception ex) {
            log.warn("Omnia Cloud会员同步批次失败，count={}", claimed.size(), ex);
            claimed.forEach(item -> outboxService.fail(item, ex.getMessage()));
        }
    }
}
