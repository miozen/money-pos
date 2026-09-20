package com.money.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.money.feature.sys.infrastructure.persistence.entity.SysPrintConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 小票打印与硬件配置 Mapper
 * 职责：专职负责 sys_print_config 表的读写
 */
@Mapper
public interface SysPrintConfigMapper extends BaseMapper<SysPrintConfig> {
    // 继承 BaseMapper，提供 selectById(1) 和 updateById 能力
}
