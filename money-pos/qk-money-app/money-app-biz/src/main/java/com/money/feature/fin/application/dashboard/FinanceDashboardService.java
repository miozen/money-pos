package com.money.feature.fin.application.dashboard;

import com.money.dto.Finance.FinanceDataVO; // 🌟 修复：明确引入主类，不用 .*

public interface FinanceDashboardService {

    // 原有：获取财务大盘数据 (带上前缀 FinanceDataVO.)
    FinanceDataVO.FinanceDashboardVO getDashboardData(String date);

    // 原有：获取渠道混合分析 (带上前缀 FinanceDataVO.)
    FinanceDataVO.ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate);

    // ==========================================
    // 🌟 8.1 核心新增：获取首页资产驾驶舱数据
    // ==========================================
    FinanceDataVO.AssetDashboardVO getAssetDashboard();
}