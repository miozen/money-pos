package com.money.controller;

import com.money.dto.Finance.FinanceDataVO;
import com.money.service.FinanceDashboardService;
import com.money.feature.fin.application.analysis.FinanceProfitService;
import com.money.service.FinanceShiftService;
import com.money.feature.fin.application.analysis.FinanceRiskService;
import com.money.service.printer.PosPrinterService; // 🌟 引入原生硬件打印驱动
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Finance", description = "财务大屏及高级报表接口")
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceDashboardService financeDashboardService;
    private final FinanceProfitService financeProfitService;
    private final FinanceShiftService financeShiftService;
    private final FinanceRiskService financeRiskService;
    private final PosPrinterService posPrinterService; // 🌟 注入打印服务

    @Operation(summary = "6.1 财务瀑布流全口径日结大屏")
    @GetMapping("/dashboard")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.FinanceDashboardVO getDashboardData(@RequestParam(required = false) String date) {
        return financeDashboardService.getDashboardData(date);
    }

    @Operation(summary = "6.2 支付渠道及虚拟抵扣占比分析")
    @GetMapping("/channel-mix")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.ChannelMixAnalysisVO getChannelMixAnalysis(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeDashboardService.getChannelMixAnalysis(startDate, endDate);
    }

    @Operation(summary = "商品利润排行榜(默认近30天)")
    @GetMapping("/profit-ranking")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<FinanceDataVO.ProfitRankVO> getProfitRanking() {
        return financeProfitService.getProfitRanking();
    }

    @Operation(summary = "满减活动复盘分析")
    @GetMapping("/campaign-review")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public List<FinanceDataVO.CampaignReviewVO> getCampaignReview() {
        return financeProfitService.getCampaignReview();
    }

    @Operation(summary = "收银交接班对账单")
    @GetMapping("/shift-handover")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.ShiftHandoverVO getShiftHandover(@RequestParam String startTime, @RequestParam(required = false) String cashierName) {
        return financeShiftService.getShiftHandover(startTime, cashierName);
    }

    // ==========================================
    // 🌟 核心新增：专门提供给桌面端调用的“交班单静默硬件打印”接口
    // ==========================================
    @Operation(summary = "静默硬件打印：收银交接班小票")
    @GetMapping("/shift-handover/print")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public void printShiftHandover(@RequestParam String startTime, @RequestParam(required = false) String cashierName) {
        // 先拉取最新的大一统账单数据
        FinanceDataVO.ShiftHandoverVO vo = financeShiftService.getShiftHandover(startTime, cashierName);
        // 发送给硬件驱动直接打黑白小票
        posPrinterService.printShiftHandover(vo);
    }

    @Operation(summary = "6.6 收银风控雷达看板")
    @GetMapping("/risk-control")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public Object getRiskControlData(@RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate) {
        return financeRiskService.getRiskSummary(startDate, endDate);
    }

    @Operation(summary = "8.1 首页资产驾驶舱核心数据")
    @GetMapping("/dashboard/asset")
    @PreAuthorize("@rbac.hasPermission('omsOrder:list')")
    public FinanceDataVO.AssetDashboardVO getAssetDashboard() {
        return financeDashboardService.getAssetDashboard();
    }
}