package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface UmsMemberBrandLevelMapper extends BaseMapper<UmsMemberBrandLevel> {

    // 3. 品牌高等级会员分布矩阵
    // 🌟 核心修复：
    // 1. 增加 INNER JOIN ums_member m，自动过滤掉物理删除后遗留的孤儿数据
    // 2. 增加 WHERE m.deleted = 0，严格过滤掉被逻辑删除的幽灵会员
    @org.apache.ibatis.annotations.Select("SELECT umbl.brand AS brandId, " +
            "umbl.level_code AS levelCode, " +
            "COUNT(umbl.member_id) AS memberCount " +
            "FROM ums_member_brand_level umbl " +
            "INNER JOIN ums_member m ON umbl.member_id = m.id " +
            "WHERE m.deleted = 0 " +
            "GROUP BY umbl.brand, umbl.level_code")
    List<Map<String, Object>> getHomeMemberDistributionData();
}
