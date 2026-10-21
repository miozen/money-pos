package com.money.feature.ums.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** UMS-owned, resumable checkpoint for one cloud member full-snapshot backfill. */
@Data
@TableName("cloud_member_sync_bootstrap")
public class CloudMemberSyncBootstrap {
    @TableId private Long tenantId;
    private String status;
    private Long cursorMemberId;
    private Long totalEnqueued;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updateTime;
}
