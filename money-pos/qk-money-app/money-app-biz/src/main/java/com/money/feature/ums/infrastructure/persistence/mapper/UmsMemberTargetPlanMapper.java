package com.money.feature.ums.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberTargetPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

@Mapper
public interface UmsMemberTargetPlanMapper extends BaseMapper<UmsMemberTargetPlan> {
    @Select("SELECT * FROM ums_member_target_plan WHERE id = #{id} FOR UPDATE")
    UmsMemberTargetPlan selectByIdForUpdate(@Param("id") Long id);
    @Update("UPDATE ums_member_target_plan SET progress_amount = progress_amount + #{delta}, update_time = NOW() " +
            "WHERE id = #{id} AND progress_amount + #{delta} >= 0 AND status = 'IN_PROGRESS'")
    int changeProgress(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
