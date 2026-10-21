package com.money.feature.ums.application.cloudsync;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Deliberately has no production defaults: cloud publishing needs explicit local configuration. */
@Data
@Component
@ConfigurationProperties(prefix = "omnia.cloud.sync")
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
}
