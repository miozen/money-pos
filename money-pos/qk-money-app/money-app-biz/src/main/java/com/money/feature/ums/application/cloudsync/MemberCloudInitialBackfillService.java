package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncBootstrap;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncBootstrapMapper;
import com.money.mapper.UmsMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Enqueues existing active members in resumable chunks; it never sends HTTP within this transaction. */
@Service
@RequiredArgsConstructor
public class MemberCloudInitialBackfillService {
    private final MemberCloudSyncProperties properties;
    private final UmsMemberMapper memberMapper;
    private final CloudMemberSyncBootstrapMapper bootstrapMapper;
    private final MemberCloudSyncOutboxService outboxService;

    @Transactional
    public int enqueueNextChunk() {
        int total = 0;
        int limit = Math.max(1, Math.min(properties.getInitialBackfillBatchSize(), 1000));
        for (Long tenantId : memberMapper.selectTenantIdsForCloudRepair()) {
            bootstrapMapper.insertIfAbsent(tenantId);
            CloudMemberSyncBootstrap checkpoint = bootstrapMapper.selectForUpdate(tenantId);
            if (checkpoint == null || "COMPLETED".equals(checkpoint.getStatus())) continue;
            boolean repairing = "REPAIRING".equals(checkpoint.getStatus());
            List<UmsMember> members = repairing
                    ? memberMapper.selectMembersForCloudRepair(tenantId, checkpoint.getCursorMemberId(), limit)
                    : memberMapper.selectActiveMembersForCloudBootstrap(tenantId, checkpoint.getCursorMemberId(), limit);
            if (members.isEmpty()) {
                bootstrapMapper.advance(tenantId, checkpoint.getCursorMemberId(), 0, "COMPLETED");
                continue;
            }
            members.forEach(member -> {
                if (repairing && Boolean.TRUE.equals(member.getDeleted())) outboxService.markTombstone(tenantId, member.getId());
                else outboxService.markUpsert(tenantId, member.getId());
            });
            Long cursor = members.get(members.size() - 1).getId();
            bootstrapMapper.advance(tenantId, cursor, members.size(), members.size() < limit ? "COMPLETED" : (repairing ? "REPAIRING" : "PENDING"));
            total += members.size();
        }
        return total;
    }

    @Transactional
    public void beginRepair() {
        for (Long tenantId : memberMapper.selectTenantIdsForCloudRepair()) {
            bootstrapMapper.insertIfAbsent(tenantId);
            bootstrapMapper.beginRepair(tenantId);
        }
    }
}
