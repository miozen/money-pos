package com.money.feature.ums.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CloudMemberSyncOutboxMapper extends BaseMapper<CloudMemberSyncOutbox> {
    @Update("INSERT INTO cloud_member_sync_outbox (tenant_id, member_id, operation, snapshot_version, status, retry_count, next_retry_at, create_time, update_time) " +
            "VALUES (#{tenantId}, #{memberId}, #{operation}, 1, 'PENDING', 0, NOW(), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE operation = VALUES(operation), snapshot_version = snapshot_version + 1, status = 'PENDING', retry_count = 0, next_retry_at = NOW(), processing_at = NULL, sent_at = NULL, last_error = NULL, update_time = NOW()")
    int markLatest(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId, @Param("operation") String operation);

    @Update("UPDATE cloud_member_sync_outbox SET status = 'FAILED', next_retry_at = NOW(), processing_at = NULL, last_error = '同步进程中断，已自动恢复重试', update_time = NOW() WHERE status = 'PROCESSING' AND (processing_at IS NULL OR processing_at < #{expiredBefore})")
    int recoverExpiredProcessing(@Param("expiredBefore") java.time.LocalDateTime expiredBefore);
}
