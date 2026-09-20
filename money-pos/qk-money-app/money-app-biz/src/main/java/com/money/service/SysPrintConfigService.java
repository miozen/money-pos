package com.money.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.money.feature.sys.infrastructure.persistence.entity.SysPrintConfig;

/**
 * 小票动态配置 Service
 */
public interface SysPrintConfigService extends IService<SysPrintConfig> {

    /**
     * 获取当前唯一的打印配置 (ID固定为1)
     */
    SysPrintConfig getConfig();

    /**
     * 更新当前唯一的打印配置 (ID强制设为1)
     */
    boolean updateConfig(SysPrintConfig config);
}
