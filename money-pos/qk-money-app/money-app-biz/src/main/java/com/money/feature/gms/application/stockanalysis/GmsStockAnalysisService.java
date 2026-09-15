package com.money.feature.gms.application.stockanalysis;

import com.money.dto.GmsGoods.GmsStockDataVO.StockAnalysisReportVO;
import java.util.List;

public interface GmsStockAnalysisService {

    /**
     * 7.5 & 7.6 进销存汇总与成本盈亏审计报表
     */
    List<StockAnalysisReportVO> getStockAnalysisReport(String startDate, String endDate, String keyword);
}
