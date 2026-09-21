package com.money.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.sys.infrastructure.persistence.entity.SysStrategy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysStrategyMapper extends BaseMapper<SysStrategy> {

    // 🌟 强行忽略所有租户拦截，确保大盘参数能被任何分店读取，绝不报 500 错！
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_strategy LIMIT 1")
    SysStrategy getGlobalStrategy();
}
