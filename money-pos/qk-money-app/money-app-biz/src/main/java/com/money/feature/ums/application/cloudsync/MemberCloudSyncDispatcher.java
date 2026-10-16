package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "omnia.cloud.sync", name = "enabled", havingValue = "true")
public class MemberCloudSyncDispatcher {
    private final MemberCloudSyncOutboxService outboxService;
    private final MemberCloudSnapshotAssembler snapshotAssembler;
    private final MemberCloudSyncProperties properties;
    private final HttpsMemberCloudSyncPublisher publisher;

    @Scheduled(fixedDelayString = "${omnia.cloud.sync.fixed-delay-ms:60000}")
    public void dispatchWhenPublisherIsInstalled() {
        List<CloudMemberSyncOutbox> claimed = outboxService.claimDue(50);
        if (claimed.isEmpty()) return;
        try {
            List<MemberCloudSyncRequestFactory.CloudSyncItem> items = claimed.stream().map(item ->
                    new MemberCloudSyncRequestFactory.CloudSyncItem(item, "UPSERT".equals(item.getOperation()) ? snapshotAssembler.assemble(item.getMemberId()) : null))
                    .collect(java.util.stream.Collectors.toList());
            if (properties.getTenantId() != null && items.stream().anyMatch(item -> !properties.getTenantId().equals(item.getOutbox().getTenantId()))) {
                throw new IllegalStateException("Outbox租户与Omnia Cloud发布器配置不一致");
            }
            publisher.publish(items);
            claimed.forEach(outboxService::acknowledge);
        } catch (Exception ex) {
            log.warn("Omnia Cloud会员同步批次失败，count={}", claimed.size(), ex);
            claimed.forEach(item -> outboxService.fail(item, ex.getMessage()));
        }
    }
}
