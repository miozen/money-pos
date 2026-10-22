package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** Local UMS-owned outbox; it never represents cloud business data. */
@Data
@TableName("cloud_member_sync_outbox")
public class CloudMemberSyncOutbox {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long memberId;
    private String operation;
    private Long snapshotVersion;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime processingAt;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
