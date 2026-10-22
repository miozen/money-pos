package com.money.feature.ums.application.cloudsync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.money.dto.cloud.MemberCloudSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCloudSyncRequestFactoryTest {
    private final MemberCloudSyncRequestFactory factory = new MemberCloudSyncRequestFactory(new ObjectMapper());

    @Test
    void signsTransformedSnapshotWithoutRawPhone() {
        MemberCloudSyncProperties properties = configured();
        CloudMemberSyncOutbox outbox = outbox("UPSERT");
        MemberCloudSnapshot snapshot = new MemberCloudSnapshot();
        snapshot.setName(" 张三 "); snapshot.setPhone("13800138000"); snapshot.setBalance(java.math.BigDecimal.TEN);
        MemberCloudSyncRequestFactory.CloudBatchRequest request = factory.build(properties,
                Collections.singletonList(new MemberCloudSyncRequestFactory.CloudSyncItem(outbox, snapshot)), 1700000000000L);

        assertThat(request.getBody()).doesNotContain("13800138000").contains("138****8000").contains("nameNormalized");
        assertThat(request.getHeaders()).containsKeys("X-Omnia-Signature", "X-Omnia-Body-SHA256", "X-Omnia-Nonce");
        assertThat(request.getHeaders().get("X-Omnia-Signature")).hasSize(64);
    }

    @Test
    void tombstoneHasNoProjectionOrPhoneMaterial() {
        MemberCloudSyncRequestFactory.CloudBatchRequest request = factory.build(configured(),
                Collections.singletonList(new MemberCloudSyncRequestFactory.CloudSyncItem(outbox("TOMBSTONE"), null)), 1700000000000L);
        assertThat(request.getBody()).doesNotContain("\"snapshot\":").doesNotContain("phone");
    }

    @Test
    void requiresReceiverToAcknowledgeTheEntireBatch() {
        factory.requireAcceptedCount("{\"accepted\":1}", 1);
        assertThatThrownBy(() -> factory.requireAcceptedCount("{\"accepted\":0}", 1)).hasMessageContaining("确认数量不匹配");
    }

    @Test
    void rejectsNonReceiverOrPlainHttpEndpoint() {
        MemberCloudSyncProperties properties = configured();
        properties.setEndpoint("http://cloud.example/internal/v1/member-snapshots:batch");
        assertThatThrownBy(() -> factory.build(properties, Collections.emptyList(), 1700000000000L)).hasMessageContaining("HTTPS端点");
    }

    private MemberCloudSyncProperties configured() {
        MemberCloudSyncProperties properties = new MemberCloudSyncProperties();
        properties.setEndpoint("https://cloud.example/internal/v1/member-snapshots:batch"); properties.setStoreId("store-a");
        properties.setSigningSecretBase64("MDEyMzQ1Njc4OWFiY2RlZg=="); properties.setSearchHmacSecretBase64("ZmVkY2JhOTg3NjU0MzIxMA=="); return properties;
    }
    private CloudMemberSyncOutbox outbox(String operation) {
        CloudMemberSyncOutbox outbox = new CloudMemberSyncOutbox(); outbox.setMemberId(7L); outbox.setSnapshotVersion(3L); outbox.setOperation(operation);
        outbox.setUpdateTime(LocalDateTime.of(2024, 1, 1, 0, 0)); return outbox;
    }
}
