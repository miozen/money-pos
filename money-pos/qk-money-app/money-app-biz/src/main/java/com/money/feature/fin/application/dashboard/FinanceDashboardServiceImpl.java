package com.money.feature.fin.application.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.money.constant.OrderStatusEnum;
import com.money.contract.goods.FinanceInventoryDocumentQuery;
import com.money.contract.goods.FinanceInventoryDocumentSnapshot;
import com.money.dto.Finance.FinanceDataVO.*;
import com.money.entity.OmsOrder;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.feature.fin.infrastructure.persistence.mapper.FinanceReportMapper;
import com.money.feature.fin.application.dashboard.FinanceDashboardService;
import com.money.feature.ums.application.member.UmsMemberService;
import com.money.feature.fin.application.dashboard.FinanceDashboardAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private final OmsOrderMapper omsOrderMapper;
    private final OmsOrderPayMapper omsOrderPayMapper;
    private final UmsMemberService umsMemberService;
    private final UmsMemberLogMapper umsMemberLogMapper;
    private final FinanceInventoryDocumentQuery financeInventoryDocumentQuery;
    private final FinanceReportMapper financeReportMapper;

    private final FinanceDashboardAssembler assembler; // 🌟 专职组装工厂

    @Override
    public AssetDashboardVO getAssetDashboard() {
        AssetDashboardVO dashboard = financeReportMapper.getTodayAssetSummary();
        if (dashboard == null) {
            dashboard = new AssetDashboardVO();
            dashboard.setTodayRealCash(BigDecimal.ZERO);
            dashboard.setTodayWaivedAmount(BigDecimal.ZERO);
            dashboard.setTodayAssetDeduct(BigDecimal.ZERO);
        }

        // 委托装配器计算比例
        assembler.assembleAssetDashboard(dashboard, financeReportMapper.getAssetComposition());
        return dashboard;
    }

    @Override
    public FinanceDashboardVO getDashboardData(String date) {
        LocalDate targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime startOfDay = LocalDateTime.of(targetDate, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(targetDate, LocalTime.MAX);
        LocalDateTime startOf7DaysAgo = LocalDateTime.of(targetDate.minusDays(6), LocalTime.MIN);

        FinanceDashboardVO vo = new FinanceDashboardVO();

        // 1. 抓取原始数据
        List<OmsOrder> dailyOrders = omsOrderMapper.selectList(new LambdaQueryWrapper<OmsOrder>()
                .ge(OmsOrder::getCreateTime, startOfDay).le(OmsOrder::getCreateTime, endOfDay)
                .in(OmsOrder::getStatus, OrderStatusEnum.getValidFinancialStatus()));

        List<FinanceInventoryDocumentSnapshot> inventoryDocs = financeInventoryDocumentQuery
                .listDailyFinancialDocuments(targetDate);

        List<Map<String, Object>> dailyNetPays = omsOrderPayMapper.getDailyPaySummary(startOfDay, endOfDay);

        List<UmsMemberLog> dailyRecharges = umsMemberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .ge(UmsMemberLog::getCreateTime, startOfDay).le(UmsMemberLog::getCreateTime, endOfDay)
                .in(UmsMemberLog::getOperateType, "RECHARGE", "REVERSAL"));

        // 强势过滤负数余额
        List<Object> balanceObjs = umsMemberService.listObjs(new LambdaQueryWrapper<UmsMember>()
                .select(UmsMember::getBalance).isNotNull(UmsMember::getBalance).gt(UmsMember::getBalance, 0));
        BigDecimal totalDebt = balanceObjs.stream().map(obj -> (BigDecimal) obj).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 获取趋势所需基础数据
        List<Map<String, Object>> paySummary = omsOrderPayMapper.getDailyPaySummary(startOf7DaysAgo, endOfDay);
        List<Map<String, Object>> rechargeSummary = umsMemberLogMapper.selectMaps(new QueryWrapper<UmsMemberLog>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(real_amount) AS totalAmt")
                .ge("create_time", startOf7DaysAgo).le("create_time", endOfDay)
                .in("operate_type", "RECHARGE", "REVERSAL").groupBy("DATE(create_time)"));
        List<Map<String, Object>> dailyOrderStats = omsOrderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr", "SUM(pay_amount) AS dailyGrossPay", "SUM(final_sales_amount) AS dailyNetPay")
                .ge("create_time", startOf7DaysAgo).le("create_time", endOfDay)
                .in("status", OrderStatusEnum.getValidFinancialStatus()).groupBy("DATE(create_time)"));

        // 2. 委托装配器组装结果
        assembler.assembleCoreMetrics(vo, dailyOrders, inventoryDocs);
        assembler.assembleIncomeAndPie(vo, dailyNetPays, dailyRecharges, totalDebt);
        assembler.assembleTrendLines(vo, targetDate, paySummary, rechargeSummary, dailyOrderStats);

        return vo;
    }

    @Override
    public ChannelMixAnalysisVO getChannelMixAnalysis(String startDate, String endDate) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : LocalDate.now().minusDays(6);
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        // 1. 抓取原始数据
        List<Map<String, Object>> paySummary = omsOrderPayMapper.getDailyPaySummary(startTime, endTime);
        List<Map<String, Object>> orderStats = omsOrderMapper.selectMaps(new QueryWrapper<OmsOrder>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS dateStr",
                        "SUM(IFNULL(actual_coupon_deduct, 0)) AS couponAmt",
                        "SUM(IFNULL(use_voucher_amount, 0)) AS voucherAmt")
                .ge("create_time", startTime).le("create_time", endTime)
                .in("status", "PAID", "PARTIAL_REFUNDED", "REFUNDED")
                .groupBy("DATE(create_time)"));

        // 2. 委托装配器组装结果
        ChannelMixAnalysisVO vo = new ChannelMixAnalysisVO();
        assembler.assembleChannelMix(vo, start, end, paySummary, orderStats);

        return vo;
    }
}
