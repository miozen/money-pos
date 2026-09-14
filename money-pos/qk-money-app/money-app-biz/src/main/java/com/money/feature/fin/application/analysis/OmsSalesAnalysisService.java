package com.money.feature.fin.application.analysis;

import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.HourlyTrafficVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.CategorySalesVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.GoodsTrendVO; // 🌟 显式导入单品趋势VO
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.web.vo.PageVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 经营分析大盘 服务接口 (V3.0 决策引擎升级版)
 */
public interface OmsSalesAnalysisService {

    SalesDashboardVO getSalesDashboard(String startDate, String endDate);

    List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension);

    List<MarketingRoiVO> getMarketingRoiAnalysis(String startDate, String endDate);

    OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime);

    PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO);

    List<HourlyTrafficVO> getTrafficAnalysis(Integer dayOfWeek);

    List<OmsSalesDataVO.TimeTrafficVO> getWeeklyTraffic();

    List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTraffic();

    // 🌟 获取商品分类销售占比
    List<CategorySalesVO> getCategorySales(String startDate, String endDate);

    // 🌟 P0-3：查询选定单品的每日连贯销量趋势
    List<GoodsTrendVO> getTopGoodsTrend(String startDate, String endDate, List<Long> goodsIds);
}