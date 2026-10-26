package com.money.feature.ums.application.cloudsync;

import lombok.Data;

/** Returned only by the explicit, loopback-only administrator reveal action. */
@Data
public class MemberCloudSyncSecretRevealDTO {
    private String signingSecretBase64;
    private String searchHmacSecretBase64;
}
