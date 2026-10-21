package com.money.feature.ums.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncBootstrap;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CloudMemberSyncBootstrapMapper extends BaseMapper<CloudMemberSyncBootstrap> {
    @Insert("INSERT IGNORE INTO cloud_member_sync_bootstrap (tenant_id, status, cursor_member_id, total_enqueued, started_at, update_time) VALUES (#{tenantId}, 'PENDING', 0, 0, NOW(), NOW())")
    int insertIfAbsent(@Param("tenantId") Long tenantId);

    @Select("SELECT * FROM cloud_member_sync_bootstrap WHERE tenant_id = #{tenantId} FOR UPDATE")
    CloudMemberSyncBootstrap selectForUpdate(@Param("tenantId") Long tenantId);

    @Update("UPDATE cloud_member_sync_bootstrap SET cursor_member_id = #{cursorMemberId}, total_enqueued = total_enqueued + #{enqueued}, status = #{status}, completed_at = CASE WHEN #{status} = 'COMPLETED' THEN NOW() ELSE NULL END, update_time = NOW() WHERE tenant_id = #{tenantId}")
    int advance(@Param("tenantId") Long tenantId, @Param("cursorMemberId") Long cursorMemberId, @Param("enqueued") long enqueued, @Param("status") String status);
}
