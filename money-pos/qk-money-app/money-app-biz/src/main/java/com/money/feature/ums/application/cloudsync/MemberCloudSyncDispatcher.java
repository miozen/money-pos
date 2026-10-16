package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(fixedDelayString = "${omnia.cloud.sync.fixed-delay-ms:60000}")
    public void dispatchWhenPublisherIsInstalled() {
        for (CloudMemberSyncOutbox item : outboxService.claimDue(50)) {
            try {
                // Materialize now so a later authorized publisher always receives the latest local state.
                snapshotAssembler.assemble(item.getMemberId());
                outboxService.fail(item, "未配置经授权的Omnia Cloud发布器");
            } catch (Exception ex) {
                log.warn("Omnia Cloud会员同步准备失败，memberId={}", item.getMemberId(), ex);
                outboxService.fail(item, ex.getMessage());
            }
        }
    }
}
