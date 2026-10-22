package com.money.feature.ums.application.cloudsync;

import lombok.Value;

import java.time.LocalDateTime;

/** Safe local operational status; it deliberately contains no endpoint, secret, or member data. */
@Value
public class MemberCloudSyncStatus {
    boolean enabled;
    boolean configurationComplete;
    String storeId;
    Long tenantId;
    long pending;
    long failed;
    long processing;
    long sent;
    String bootstrapStatus;
    Long bootstrapCursorMemberId;
    Long bootstrapTotalEnqueued;
    LocalDateTime bootstrapCompletedAt;
    LocalDateTime lastSentAt;
}
