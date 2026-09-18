package com.money.feature.gms.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsTurnoverWarningSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 周转预警快照 Mapper
 */
@Mapper
@InterceptorIgnore(tenantLine = "true") // 🌟 核心修复：贴上免死金牌，禁止底层自动拼接 tenant_id
public interface GmsTurnoverWarningSnapshotMapper extends BaseMapper<GmsTurnoverWarningSnapshot> {
}
