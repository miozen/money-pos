package com.money.feature.ums.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberQuantityRight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UmsMemberQuantityRightMapper extends BaseMapper<UmsMemberQuantityRight> {
    @Select("SELECT * FROM ums_member_quantity_right WHERE id = #{id} FOR UPDATE")
    UmsMemberQuantityRight selectByIdForUpdate(@Param("id") Long id);
    @Update("UPDATE ums_member_quantity_right SET remaining_quantity = remaining_quantity + #{delta}, " +
            "picked_quantity = picked_quantity + #{pickedDelta}, update_time = NOW() " +
            "WHERE id = #{id} AND remaining_quantity + #{delta} >= 0")
    int changeBalance(@Param("id") Long id, @Param("delta") int delta, @Param("pickedDelta") int pickedDelta);
}
