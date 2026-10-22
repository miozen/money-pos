package com.money.feature.ums.application.cloudsync;

import lombok.Data;

/** UI contract deliberately returns only whether local secrets have been set. */
@Data
public class MemberCloudSyncConfigurationDTO {
    private String endpoint;
    private String storeId;
    private String signingSecretBase64;
    private String searchHmacSecretBase64;
    private boolean signingSecretConfigured;
    private boolean searchHmacSecretConfigured;
    private boolean enabled;
}
