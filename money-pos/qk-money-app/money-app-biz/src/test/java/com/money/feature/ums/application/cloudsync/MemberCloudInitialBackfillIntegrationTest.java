package com.money.feature.ums.application.cloudsync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncBootstrap;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncBootstrapMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncOutboxMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.support.TradeFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberCloudInitialBackfillIntegrationTest {
    @Autowired private TradeFixture tradeFixture;
    @Autowired private CloudMemberSyncOutboxMapper outboxMapper;
    @Autowired private CloudMemberSyncBootstrapMapper bootstrapMapper;
    @Autowired private MemberCloudInitialBackfillService backfillService;
    @Autowired private MemberCloudSyncProperties properties;
    @Autowired private UmsMemberMapper memberMapper;
    @Autowired private MemberCloudSyncOutboxService outboxService;

    @Test
    void enqueuesEveryActiveMemberOnceAndCompletesCheckpoint() {
        int previousSize = properties.getInitialBackfillBatchSize();
        properties.setInitialBackfillBatchSize(1000);
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember first = tradeFixture.createMember("ba" + suffix, BigDecimal.ZERO);
        UmsMember second = tradeFixture.createMember("bb" + suffix, BigDecimal.ZERO);
        long tenantId = 0L;
        bootstrapMapper.deleteById(tenantId);
        try {
            assertThat(backfillService.enqueueNextChunk()).isGreaterThanOrEqualTo(2);
            java.util.List<CloudMemberSyncOutbox> rows = outboxMapper.selectList(new LambdaQueryWrapper<CloudMemberSyncOutbox>().in(CloudMemberSyncOutbox::getMemberId, first.getId(), second.getId()));
            assertThat(rows).extracting(CloudMemberSyncOutbox::getOperation).containsOnly("UPSERT");
            CloudMemberSyncBootstrap checkpoint = bootstrapMapper.selectById(tenantId);
            assertThat(checkpoint.getStatus()).isEqualTo("COMPLETED");
            java.util.List<Long> versions = rows.stream().map(CloudMemberSyncOutbox::getSnapshotVersion).collect(java.util.stream.Collectors.toList());
            backfillService.enqueueNextChunk();
            assertThat(outboxMapper.selectList(new LambdaQueryWrapper<CloudMemberSyncOutbox>().in(CloudMemberSyncOutbox::getMemberId, first.getId(), second.getId())))
                    .extracting(CloudMemberSyncOutbox::getSnapshotVersion).containsExactlyInAnyOrderElementsOf(versions);
        } finally {
            properties.setInitialBackfillBatchSize(previousSize);
        }
    }

    @Test
    void explicitRepairAlsoCreatesTombstoneForDeletedMember() {
        int previousSize = properties.getInitialBackfillBatchSize();
        properties.setInitialBackfillBatchSize(1000);
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember deleted = tradeFixture.createMember("deleted" + suffix, BigDecimal.ZERO);
        deleted.setDeleted(true);
        memberMapper.updateById(deleted);
        try {
            backfillService.beginRepair();
            backfillService.enqueueNextChunk();
            CloudMemberSyncOutbox row = outboxMapper.selectOne(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                    .eq(CloudMemberSyncOutbox::getMemberId, deleted.getId()));
            assertThat(row).isNotNull();
            assertThat(row.getOperation()).isEqualTo("TOMBSTONE");
        } finally {
            properties.setInitialBackfillBatchSize(previousSize);
        }
    }

    @Test
    void recoversLegacyProcessingRecordWithoutProcessingTimestamp() {
        UmsMember member = tradeFixture.createMember("recover" + Long.toString(System.nanoTime(), 36), BigDecimal.ZERO);
        outboxService.markUpsert(member.getId());
        CloudMemberSyncOutbox row = outboxMapper.selectOne(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .eq(CloudMemberSyncOutbox::getMemberId, member.getId()));
        row.setStatus("PROCESSING");
        row.setProcessingAt(null);
        outboxMapper.updateById(row);
        assertThat(outboxService.recoverExpiredProcessing()).isGreaterThanOrEqualTo(1);
        assertThat(outboxMapper.selectById(row.getId()).getStatus()).isEqualTo("FAILED");
    }
}
