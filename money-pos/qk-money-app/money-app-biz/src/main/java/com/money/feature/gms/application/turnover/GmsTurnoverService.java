package com.money.feature.gms.application.turnover;

import com.money.dto.GmsGoods.TurnoverDataVO.TurnoverDashboardVO;

public interface GmsTurnoverService {
    TurnoverDashboardVO getTurnoverWarnings();
    java.util.Map<String, Object> getWarningTrend();
}
