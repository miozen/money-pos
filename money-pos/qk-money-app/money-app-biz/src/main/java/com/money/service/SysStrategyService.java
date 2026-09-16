package com.money.service;

import com.money.entity.SysStrategy;

/**
 * 全局经营策略的应用服务边界。
 */
public interface SysStrategyService {

    /**
     * 返回全局策略；数据库尚未初始化时返回可安全读取的空对象。
     */
    SysStrategy getGlobalStrategy();

    /**
     * 保存唯一的全局策略记录。
     */
    void saveGlobalStrategy(SysStrategy strategy);
}
