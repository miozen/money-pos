package com.money.feature.fin.application.analysis;

import java.util.Map;

public interface FinanceRiskService {
    /**
     * 获取风控雷达综合数据
     */
    Map<String, Object> getRiskSummary(String startDate, String endDate);
}