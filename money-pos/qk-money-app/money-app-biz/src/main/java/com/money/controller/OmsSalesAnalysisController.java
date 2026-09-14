package com.money.controller;

import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.MarketingRoiVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.CategorySalesVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.GoodsTrendVO; // 🌟 导入单品趋势 VO
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.feature.fin.application.analysis.OmsSalesAnalysisService;
import com.money.web.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

@Tag(name = "OmsAnalysis", description = "订单大盘与客流分析")
@RestController
@RequestMapping("/oms/analysis")
@RequiredArgsConstructor
public class OmsSalesAnalysisController {

    private final OmsSalesAnalysisService omsSalesAnalysisService;

    @GetMapping("/dashboard")
    @Operation(summary = "获取门店经营作战室核心大盘数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public SalesDashboardVO getSalesDashboard(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getSalesDashboard(startDate, endDate);
    }

    @GetMapping("/report")
    @Operation(summary = "按日/周/月拉取业绩汇总列表")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<PerformanceReportVO> getPerformanceReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "DAILY") String dimension) {
        return omsSalesAnalysisService.getPerformanceReport(startDate, endDate, dimension);
    }

    @GetMapping("/marketing-roi")
    @Operation(summary = "获取营销活动投入产出比数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<MarketingRoiVO> getMarketingRoiAnalysis(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getMarketingRoiAnalysis(startDate, endDate);
    }

    @GetMapping("/audit-page")
    @Operation(summary = "获取利润异常审计分页数据")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        return omsSalesAnalysisService.getProfitAuditPage(queryDTO);
    }

    @Operation(summary = "8.2 获取客流价值分析(办事罗盘)")
    @GetMapping("/traffic")
    public List<OmsSalesDataVO.HourlyTrafficVO> getTrafficAnalysis(
            @RequestParam(required = false) Integer dayOfWeek) {
        return omsSalesAnalysisService.getTrafficAnalysis(dayOfWeek);
    }

    @Operation(summary = "按周宏观大盘(周一至周日)")
    @GetMapping("/weekly-traffic")
    public List<OmsSalesDataVO.TimeTrafficVO> getWeeklyTraffic() {
        return omsSalesAnalysisService.getWeeklyTraffic();
    }

    @Operation(summary = "按月宏观潮汐(1号至31号)")
    @GetMapping("/monthly-traffic")
    public List<OmsSalesDataVO.TimeTrafficVO> getMonthlyTraffic() {
        return omsSalesAnalysisService.getMonthlyTraffic();
    }

    @GetMapping("/category-sales")
    @Operation(summary = "获取商品分类销售占比")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<CategorySalesVO> getCategorySales(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return omsSalesAnalysisService.getCategorySales(startDate, endDate);
    }

    // ==========================================
    // 🌟 P0-3 核心新增：专门供前端单品榜单勾选联动的趋势查询接口
    // ==========================================
    @GetMapping("/top-goods-trend")
    @Operation(summary = "查询选定单品的每日连贯销量趋势")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<GoodsTrendVO> getTopGoodsTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam String goodsIds) {

        // 将逗号分隔的字符串快速转为 Long 列表
        List<Long> idList = Arrays.stream(goodsIds.split(","))
                .filter(s -> !s.trim().isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

        return omsSalesAnalysisService.getTopGoodsTrend(startDate, endDate, idList);
    }
}