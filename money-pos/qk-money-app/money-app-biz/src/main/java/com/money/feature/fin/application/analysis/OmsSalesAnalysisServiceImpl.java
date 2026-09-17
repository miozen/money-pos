package com.money.feature.fin.application.analysis;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.money.contract.goods.BrandNameQuery;
import com.money.contract.trade.FinanceOperatingAnalysisQuery;
import com.money.contract.trade.FinanceOperatingMetricSnapshot;
import com.money.contract.trade.FinanceDashboardBrandSalesSnapshot;
import com.money.contract.trade.FinanceDashboardMemberDailySnapshot;
import com.money.contract.trade.FinanceDashboardTopGoodsSnapshot;
import com.money.contract.trade.FinanceSalesDashboardQuery;
import com.money.dto.OmsOrder.OmsOrderQueryDTO;
import com.money.dto.OmsOrder.OmsSalesDataVO.*;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.dto.OmsOrder.ProfitAuditVO;
import com.money.entity.SysStrategy;
import com.money.mapper.*;
import com.money.feature.fin.application.analysis.OmsSalesAnalysisService;
import com.money.feature.fin.application.analysis.FinanceMetricAssembler;
import com.money.util.PageUtil;
import com.money.web.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 经营分析大盘 业务实现类 (V4.0 装配器解耦版)
 */
@Service
@RequiredArgsConstructor
public class OmsSalesAnalysisServiceImpl implements OmsSalesAnalysisService {

    private final SysStrategyMapper sysStrategyMapper;
    private final FinanceOperatingAnalysisQuery financeOperatingAnalysisQuery;
    private final FinanceSalesDashboardQuery financeSalesDashboardQuery;
    private final BrandNameQuery brandNameQuery;
    private final OmsOrderAnalysisMapper omsOrderAnalysisMapper;
    private final OmsOrderTrafficMapper omsOrderTrafficMapper;
    private final OmsOrderAuditMapper omsOrderAuditMapper;

    private final FinanceMetricAssembler metricAssembler; // 🌟 专职处理复杂的拼装与计算

    // 🌟 全局统一基准时间解析：所有涉及报表的，全部以 create_time 作为查询基准
    private LocalDateTime parseStartTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return LocalDate.now().minusDays(29).atStartOfDay();
        if (dateStr.length() == 10) return LocalDate.parse(dateStr).atStartOfDay();
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private LocalDateTime parseEndTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return LocalDate.now().atTime(LocalTime.MAX);
        if (dateStr.length() == 10) return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public SalesDashboardVO getSalesDashboard(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);
        SalesDashboardVO vo = new SalesDashboardVO();

        // 1. 获取原子统计数据，并委托装配基础图表
        List<FinanceOperatingMetricSnapshot> dailyStats = financeOperatingAnalysisQuery
                .listPeriodMetrics(startTime, endTime, "DAILY");
        metricAssembler.assembleBasicDashboard(vo, dailyStats, startTime, endTime);

        // 2. 获取排行数据，直接装填
        vo.setTopGoodsRanking(toTopGoodsRanking(financeSalesDashboardQuery.listTopGoods(startTime, endTime)));
        vo.setBrandDistribution(toBrandDistribution(financeSalesDashboardQuery.listBrandSales(startTime, endTime)));

        // 3. 获取会员双线数据，并委托装配趋势图
        List<FinanceDashboardMemberDailySnapshot> memberStats = financeSalesDashboardQuery
                .listDailyMemberMetrics(startTime, endTime);
        vo.setMemberTrend(metricAssembler.assembleMemberTrend(memberStats, startTime, endTime));

        return vo;
    }

    @Override
    public List<PerformanceReportVO> getPerformanceReport(String startDate, String endDate, String dimension) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        List<FinanceOperatingMetricSnapshot> stats = financeOperatingAnalysisQuery
                .listPeriodMetrics(startTime, endTime, dimension);
        List<PerformanceReportVO> result = new ArrayList<>();
        for (FinanceOperatingMetricSnapshot stat : stats) {
            result.add(new PerformanceReportVO(
                    stat.getPeriod(), (int) stat.getOrderCount(), (int) stat.getGoodsCount(),
                    stat.getNetSalesAmount(), calculateAsp(stat.getNetSalesAmount(), stat.getOrderCount())
            ));
        }
        Collections.reverse(result);
        return result;
    }

    @Override
    public List<MarketingRoiVO> getMarketingRoiAnalysis(String startDate, String endDate) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        List<MarketingRoiVO> results = omsOrderAnalysisMapper.getMarketingRoiStats(startTime, endTime);
        // 委托装配器计算 ROI 乘数与客单价
        return metricAssembler.calculateMarketingRoi(results);
    }

    @Override
    public OrderCountVO countOrderAndSales(LocalDateTime startTime, LocalDateTime endTime) {
        List<FinanceOperatingMetricSnapshot> stats = financeOperatingAnalysisQuery
                .listPeriodMetrics(startTime, endTime, "DAILY");
        // 委托装配器进行全局汇总累加
        return metricAssembler.aggregateTotalMetrics(stats);
    }

    private BigDecimal calculateAsp(BigDecimal netSalesAmount, long orderCount) {
        return orderCount == 0 ? BigDecimal.ZERO
                : (netSalesAmount == null ? BigDecimal.ZERO : netSalesAmount)
                .divide(BigDecimal.valueOf(orderCount), 2, java.math.RoundingMode.HALF_UP);
    }

    private List<GoodsSalesRankVO> toTopGoodsRanking(List<FinanceDashboardTopGoodsSnapshot> snapshots) {
        List<GoodsSalesRankVO> result = new ArrayList<>();
        for (FinanceDashboardTopGoodsSnapshot snapshot : snapshots) {
            result.add(new GoodsSalesRankVO(snapshot.getGoodsId(), snapshot.getGoodsName(),
                    (int) snapshot.getSalesQuantity(), snapshot.getSalesAmount()));
        }
        return result;
    }

    private List<BrandSalesVO> toBrandDistribution(List<FinanceDashboardBrandSalesSnapshot> snapshots) {
        Set<String> brandIds = new HashSet<>();
        for (FinanceDashboardBrandSalesSnapshot snapshot : snapshots) {
            if (snapshot.getBrandId() != null) brandIds.add(String.valueOf(snapshot.getBrandId()));
        }
        Map<String, String> namesById = brandNameQuery.findNamesByIds(brandIds);
        List<BrandSalesVO> result = new ArrayList<>();
        for (FinanceDashboardBrandSalesSnapshot snapshot : snapshots) {
            String brandName = snapshot.getBrandId() == null ? null
                    : namesById.get(String.valueOf(snapshot.getBrandId()));
            result.add(new BrandSalesVO(brandName == null ? "无品牌/未知" : brandName,
                    snapshot.getSalesAmount()));
        }
        return result;
    }

    @Override
    public PageVO<ProfitAuditVO> getProfitAuditPage(OmsOrderQueryDTO queryDTO) {
        Page<ProfitAuditVO> page = omsOrderAuditMapper.getProfitAuditPage(
                PageUtil.toPage(queryDTO), queryDTO.getOrderNo(), queryDTO.getStatus()
        );
        return PageUtil.toPageVO(page);
    }

    // ==========================================
    // 🌟 核心升级：客流罗盘与潮汐趋势，彻底剥夺前端计算权
    // ==========================================

    @Override
    public List<HourlyTrafficVO> getTrafficAnalysis(Integer dayOfWeek) {
        Double divisor = (dayOfWeek != null) ? 4.0 : 28.0;
        Integer mysqlDow = (dayOfWeek != null) ? ((dayOfWeek == 7) ? 1 : (dayOfWeek + 1)) : null;

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(28);

        // Mapper 已经升级，现在会同时返回 avg (平均) 和 total (总数)
        List<HourlyTrafficVO> dbData = omsOrderTrafficMapper.getHourlyTrafficAnalysis(startTime, endTime, mysqlDow, divisor);
        Map<Integer, HourlyTrafficVO> dataMap = new HashMap<>();
        if (dbData != null) {
            for (HourlyTrafficVO vo : dbData) {
                dataMap.put(vo.getHour(), vo);
            }
        }

        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        BigDecimal safeOrderThreshold = new BigDecimal("1.0");
        BigDecimal safeValueThreshold = new BigDecimal("50.0");
        if (strategy != null) {
            if (strategy.getTrafficOrderThreshold() != null) safeOrderThreshold = strategy.getTrafficOrderThreshold();
            if (strategy.getTrafficValueThreshold() != null) safeValueThreshold = strategy.getTrafficValueThreshold();
        }

        List<HourlyTrafficVO> full24Hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            HourlyTrafficVO vo = dataMap.getOrDefault(i, new HourlyTrafficVO());
            if (vo.getHour() == null) vo.setHour(i);

            // 🌟 防御编程：防止 SQL 查不到数据导致前端空指针
            if (vo.getAvgOrderCount() == null) vo.setAvgOrderCount(BigDecimal.ZERO);
            if (vo.getAvgSalesAmount() == null) vo.setAvgSalesAmount(BigDecimal.ZERO);
            if (vo.getTotalOrderCount() == null) vo.setTotalOrderCount(BigDecimal.ZERO);
            if (vo.getTotalSalesAmount() == null) vo.setTotalSalesAmount(BigDecimal.ZERO);

            // 🌟 核心：将确切的“采样天数”下发给前端，前端无需再硬编码猜逻辑
            vo.setSampleDays(divisor.intValue());

            boolean isSafeToLeave = vo.getAvgOrderCount().compareTo(safeOrderThreshold) < 0
                    && vo.getAvgSalesAmount().compareTo(safeValueThreshold) < 0;
            vo.setSuggestion(isSafeToLeave ? "OUT" : "STAY");

            full24Hours.add(vo);
        }
        return full24Hours;
    }

    @Override
    public List<TimeTrafficVO> getWeeklyTraffic() {
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        int days = (strategy != null && strategy.getWeeklyAnalysisDays() != null) ? strategy.getWeeklyAnalysisDays() : 90;
        LocalDateTime endTime = LocalDateTime.now();

        Double divisor = days / 7.0; // 计算周期倍数
        List<TimeTrafficVO> res = omsOrderTrafficMapper.getWeeklyTrafficAnalysis(endTime.minusDays(days), endTime, divisor);

        // 🌟 下发采样周期系数
        if(res != null) {
            for (TimeTrafficVO vo : res) {
                if (vo.getTotalOrderCount() == null) vo.setTotalOrderCount(BigDecimal.ZERO);
                if (vo.getTotalSalesAmount() == null) vo.setTotalSalesAmount(BigDecimal.ZERO);
                vo.setSampleDays(divisor);
            }
        }
        return res;
    }

    @Override
    public List<TimeTrafficVO> getMonthlyTraffic() {
        SysStrategy strategy = sysStrategyMapper.getGlobalStrategy();
        int days = (strategy != null && strategy.getMonthlyAnalysisDays() != null) ? strategy.getMonthlyAnalysisDays() : 180;
        LocalDateTime endTime = LocalDateTime.now();

        Double divisor = days / 30.43; // 换算成几个标准月
        List<TimeTrafficVO> res = omsOrderTrafficMapper.getMonthlyTrafficAnalysis(endTime.minusDays(days), endTime, divisor);

        // 🌟 下发采样周期系数
        if(res != null) {
            for (TimeTrafficVO vo : res) {
                if (vo.getTotalOrderCount() == null) vo.setTotalOrderCount(BigDecimal.ZERO);
                if (vo.getTotalSalesAmount() == null) vo.setTotalSalesAmount(BigDecimal.ZERO);
                vo.setSampleDays(divisor);
            }
        }
        return res;
    }

    @Override
    public List<CategorySalesVO> getCategorySales(String startDate, String endDate) {
        return omsOrderAnalysisMapper.getCategorySalesDistribution(parseStartTime(startDate), parseEndTime(endDate));
    }

    @Override
    public List<GoodsTrendVO> getTopGoodsTrend(String startDate, String endDate, List<Long> goodsIds) {
        LocalDateTime startTime = parseStartTime(startDate);
        LocalDateTime endTime = parseEndTime(endDate);

        List<DailyGoodsStatDTO> rawStats = omsOrderAnalysisMapper.getDailyGoodsStats(startTime, endTime, goodsIds);
        // 委托装配器填装多维数组
        return metricAssembler.assembleGoodsTrend(rawStats, goodsIds, startTime, endTime);
    }
}
