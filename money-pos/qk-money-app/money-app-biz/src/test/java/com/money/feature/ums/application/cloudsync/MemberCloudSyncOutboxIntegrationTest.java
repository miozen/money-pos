package com.money.feature.ums.application.cloudsync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.cloud.MemberCloudSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
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
class MemberCloudSyncOutboxIntegrationTest {
    @Autowired private TradeFixture tradeFixture;
    @Autowired private UmsMemberMapper memberMapper;
    @Autowired private CloudMemberSyncOutboxMapper outboxMapper;
    @Autowired private MemberCloudSyncOutboxService outboxService;
    @Autowired private MemberCloudSnapshotAssembler snapshotAssembler;

    @Test
    void coalescesLatestMemberOperationAndBuildsCurrentAssetSnapshot() {
        UmsMember member = tradeFixture.createMember(Long.toString(System.nanoTime(), 36), new BigDecimal("12.50"));
        member.setCoupon(new BigDecimal("3.20"));
        memberMapper.updateById(member);

        outboxService.markUpsert(member.getId());
        outboxService.markUpsert(member.getId());
        outboxService.markTombstone(member.getId());

        CloudMemberSyncOutbox row = outboxMapper.selectOne(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .eq(CloudMemberSyncOutbox::getMemberId, member.getId()));
        assertThat(row).extracting(CloudMemberSyncOutbox::getOperation, CloudMemberSyncOutbox::getSnapshotVersion,
                        CloudMemberSyncOutbox::getStatus).containsExactly("TOMBSTONE", 3L, "PENDING");

        MemberCloudSnapshot snapshot = snapshotAssembler.assemble(member.getId());
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getBalance()).isEqualByComparingTo("12.50");
        assertThat(snapshot.getCouponBalance()).isEqualByComparingTo("3.20");
        assertThat(snapshot.getStatus()).isEqualTo("ACTIVE");
    }
}
