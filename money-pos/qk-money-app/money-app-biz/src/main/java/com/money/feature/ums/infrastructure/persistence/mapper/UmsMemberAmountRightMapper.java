package com.money.feature.ums.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberAmountRight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

@Mapper
public interface UmsMemberAmountRightMapper extends BaseMapper<UmsMemberAmountRight> {
    @Select("SELECT * FROM ums_member_amount_right WHERE id = #{id} FOR UPDATE")
    UmsMemberAmountRight selectByIdForUpdate(@Param("id") Long id);
    @Update("UPDATE ums_member_amount_right SET remaining_amount = remaining_amount + #{delta}, update_time = NOW() " +
            "WHERE id = #{id} AND remaining_amount + #{delta} >= 0")
    int changeBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
