package com.money.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.money.feature.sys.infrastructure.persistence.entity.SysPrintConfig;
import com.money.mapper.SysPrintConfigMapper;
import com.money.service.SysPrintConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysPrintConfigServiceImpl extends ServiceImpl<SysPrintConfigMapper, SysPrintConfig> implements SysPrintConfigService {

    // 🌟 业务强制：本系统只支持 ID 为 1 的配置记录
    private static final Long CONFIG_ID_ONE = 1L;

    @Override
    public SysPrintConfig getConfig() {
        return this.getById(CONFIG_ID_ONE);
    }

    @Override
    public boolean updateConfig(SysPrintConfig config) {
        // 🌟 核心防误触：不管前端传什么ID过来，统统给强制设定为 1，确保覆盖更新
        config.setId(CONFIG_ID_ONE);
        return this.updateById(config);
    }
}
