package com.money.feature.ums.application.cloudsync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncBootstrap;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncBootstrapMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only operational projection of the local outbox and initial-backfill checkpoint. */
@Service
@RequiredArgsConstructor
public class MemberCloudSyncStatusService {
    private final MemberCloudSyncProperties properties;
    private final CloudMemberSyncOutboxMapper outboxMapper;
    private final CloudMemberSyncBootstrapMapper bootstrapMapper;

    public MemberCloudSyncStatus status() {
        Long tenantId = properties.getTenantId();
        CloudMemberSyncBootstrap bootstrap = tenantId == null ? null : bootstrapMapper.selectById(tenantId);
        return new MemberCloudSyncStatus(
                properties.isEnabled(), properties.hasCompletePublishingConfiguration(), properties.isInitialBackfillEnabled(),
                properties.getStoreId(), tenantId,
                count(tenantId, "PENDING"), count(tenantId, "FAILED"), count(tenantId, "PROCESSING"), count(tenantId, "SENT"),
                bootstrap == null ? "NOT_STARTED" : bootstrap.getStatus(),
                bootstrap == null ? null : bootstrap.getCursorMemberId(),
                bootstrap == null ? null : bootstrap.getTotalEnqueued(),
                bootstrap == null ? null : bootstrap.getCompletedAt(), lastSentAt(tenantId));
    }

    private long count(Long tenantId, String status) {
        return outboxMapper.selectCount(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .eq(tenantId != null, CloudMemberSyncOutbox::getTenantId, tenantId)
                .eq(CloudMemberSyncOutbox::getStatus, status));
    }

    private java.time.LocalDateTime lastSentAt(Long tenantId) {
        CloudMemberSyncOutbox latest = outboxMapper.selectOne(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .eq(tenantId != null, CloudMemberSyncOutbox::getTenantId, tenantId)
                .isNotNull(CloudMemberSyncOutbox::getSentAt).orderByDesc(CloudMemberSyncOutbox::getSentAt).last("LIMIT 1"));
        return latest == null ? null : latest.getSentAt();
    }
}
