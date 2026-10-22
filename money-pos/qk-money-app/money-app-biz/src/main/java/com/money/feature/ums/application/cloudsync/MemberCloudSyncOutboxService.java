package com.money.feature.ums.application.cloudsync;

import com.money.context.TenantContextHolder;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncOutboxMapper;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Marks member query projections in the caller's existing database transaction. */
@Service
@RequiredArgsConstructor
public class MemberCloudSyncOutboxService {
    private final CloudMemberSyncOutboxMapper mapper;

    public void markUpsert(Long memberId) { mark(memberId, "UPSERT"); }
    public void markUpsert(Long tenantId, Long memberId) { mark(tenantId, memberId, "UPSERT"); }
    public void markTombstone(Long memberId) { mark(memberId, "TOMBSTONE"); }
    public void markTombstone(Long tenantId, Long memberId) { mark(tenantId, memberId, "TOMBSTONE"); }

    /** Claims a bounded batch using compare-and-set state changes; safe for future multi-task deployments. */
    public List<CloudMemberSyncOutbox> claimDue(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        LocalDateTime now = LocalDateTime.now();
        List<CloudMemberSyncOutbox> candidates = mapper.selectList(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .in(CloudMemberSyncOutbox::getStatus, "PENDING", "FAILED")
                .le(CloudMemberSyncOutbox::getNextRetryAt, now)
                .orderByAsc(CloudMemberSyncOutbox::getId).last("LIMIT " + limit));
        List<CloudMemberSyncOutbox> claimed = new ArrayList<>();
        for (CloudMemberSyncOutbox candidate : candidates) {
            int changed = mapper.update(null, new LambdaUpdateWrapper<CloudMemberSyncOutbox>()
                    .set(CloudMemberSyncOutbox::getStatus, "PROCESSING").set(CloudMemberSyncOutbox::getProcessingAt, now)
                    .eq(CloudMemberSyncOutbox::getId, candidate.getId())
                    .eq(CloudMemberSyncOutbox::getSnapshotVersion, candidate.getSnapshotVersion())
                    .in(CloudMemberSyncOutbox::getStatus, "PENDING", "FAILED"));
            if (changed == 1) { candidate.setStatus("PROCESSING"); claimed.add(candidate); }
        }
        return claimed;
    }

    public void acknowledge(CloudMemberSyncOutbox item) {
        if (item == null) return;
        mapper.update(null, new LambdaUpdateWrapper<CloudMemberSyncOutbox>()
                .set(CloudMemberSyncOutbox::getStatus, "SENT").set(CloudMemberSyncOutbox::getSentAt, LocalDateTime.now())
                .set(CloudMemberSyncOutbox::getLastError, null).set(CloudMemberSyncOutbox::getProcessingAt, null).eq(CloudMemberSyncOutbox::getId, item.getId())
                .eq(CloudMemberSyncOutbox::getSnapshotVersion, item.getSnapshotVersion()).eq(CloudMemberSyncOutbox::getStatus, "PROCESSING"));
    }

    public void fail(CloudMemberSyncOutbox item, String error) {
        if (item == null) return;
        int retries = (item.getRetryCount() == null ? 0 : item.getRetryCount()) + 1;
        long delaySeconds = Math.min(3600L, 30L * (1L << Math.min(retries - 1, 6)));
        mapper.update(null, new LambdaUpdateWrapper<CloudMemberSyncOutbox>()
                .set(CloudMemberSyncOutbox::getStatus, "FAILED").set(CloudMemberSyncOutbox::getRetryCount, retries)
                .set(CloudMemberSyncOutbox::getNextRetryAt, LocalDateTime.now().plusSeconds(delaySeconds)).set(CloudMemberSyncOutbox::getProcessingAt, null)
                .set(CloudMemberSyncOutbox::getLastError, trim(error)).eq(CloudMemberSyncOutbox::getId, item.getId())
                .eq(CloudMemberSyncOutbox::getSnapshotVersion, item.getSnapshotVersion()).eq(CloudMemberSyncOutbox::getStatus, "PROCESSING"));
    }
    private void mark(Long memberId, String operation) {
        if (memberId == null) return;
        Long tenantId = TenantContextHolder.getTenant() == null ? 0L : TenantContextHolder.getTenant();
        mark(tenantId, memberId, operation);
    }
    private void mark(Long tenantId, Long memberId, String operation) { if (memberId != null) mapper.markLatest(tenantId == null ? 0L : tenantId, memberId, operation); }
    public int recoverExpiredProcessing() { return mapper.recoverExpiredProcessing(LocalDateTime.now().minusMinutes(5)); }
    private String trim(String value) { return value == null ? null : value.substring(0, Math.min(500, value.length())); }
}
