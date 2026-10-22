package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Singleton local POS configuration; it is not cloud business data. */
@Data
@TableName("cloud_member_sync_config")
public class CloudMemberSyncConfig {
    @TableId private Long id;
    private Boolean enabled;
    private String endpoint;
    private String storeId;
    private Long tenantId;
    private String signingSecretBase64;
    private String searchHmacSecretBase64;
    private Integer initialBackfillBatchSize;
}
