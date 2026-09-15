package com.money.feature.home.application;

import java.time.LocalDate;
import java.util.Map;

/**
 * 🌟 门店决策引擎服务 (Decision Engine)
 */
public interface DecisionEngineService {

    /**
     * 1. 快照补偿机制：检查并生成过去 N 天缺失的经营快照
     */
    void compensateSnapshots(int daysToCheck);

    /**
     * 2. 生成单日快照：抓取当天的所有业务数据并落盘
     */
    void generateDailySnapshot(LocalDate date);

    /**
     * 3. 获取带诊断警报的单日核心数据
     */
    Map<String, Object> getTodayDashboardWithAlerts();

    /**
     * 4. 🌟 新增：全维度大盘聚合接口 (直供前端新版作战室)
     */
    Map<String, Object> getComprehensiveDashboard();
}
