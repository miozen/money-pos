package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncConfig;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.net.URI;

/** Deliberately has no production defaults: cloud publishing needs explicit local configuration. */
@Data
@Component
public class MemberCloudSyncProperties {
    private boolean enabled = false;
    private long fixedDelayMs = 60000L;
    private String endpoint;
    private String storeId;
    private Long tenantId;
    private String keyId;
    private String signingSecretBase64;
    private String searchHmacSecretBase64;
    /** Explicitly opt-in one-time bootstrap; the checkpoint prevents restart replays after completion. */
    private boolean initialBackfillEnabled = false;
    private int initialBackfillBatchSize = 500;

    public void apply(CloudMemberSyncConfig config) {
        if (config == null) return;
        enabled = Boolean.TRUE.equals(config.getEnabled());
        endpoint = config.getEndpoint();
        storeId = config.getStoreId();
        tenantId = config.getTenantId();
        keyId = config.getKeyId();
        signingSecretBase64 = config.getSigningSecretBase64();
        searchHmacSecretBase64 = config.getSearchHmacSecretBase64();
        initialBackfillEnabled = Boolean.TRUE.equals(config.getInitialBackfillEnabled());
        if (config.getInitialBackfillBatchSize() != null) initialBackfillBatchSize = config.getInitialBackfillBatchSize();
    }

    /** Safe readiness predicate for the administrator status screen; it never exposes credential values. */
    public boolean hasCompletePublishingConfiguration() {
        return present(endpoint) && isReceiverEndpoint(endpoint) && present(storeId) && present(keyId)
                && present(signingSecretBase64) && present(searchHmacSecretBase64);
    }

    private boolean isReceiverEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && "/internal/v1/member-snapshots:batch".equals(uri.getPath());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean present(String value) { return value != null && !value.trim().isEmpty(); }
}
