package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.OmsOrder.AnalysisAtomicDataDTO;
import com.money.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import com.money.mapper.OmsOrderAnalysisMapper;
import com.money.service.GmsGoodsService; // 🌟 重新引回商品服务
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionEngineServiceImpl implements DecisionEngineService {

    private final OmsDailySummaryMapper omsDailySummaryMapper;
    private final OmsOrderAnalysisMapper omsOrderAnalysisMapper;
    private final JdbcTemplate jdbcTemplate;
    private final GmsGoodsService gmsGoodsService; // 🌟 注入靠谱的算库存管家

    @Override
    public void compensateSnapshots(int daysToCheck) {
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= daysToCheck; i++) {
            LocalDate targetDate = today.minusDays(i);
            boolean exists = omsDailySummaryMapper.exists(
                    new LambdaQueryWrapper<OmsDailySummary>().eq(OmsDailySummary::getRecordDate, targetDate)
            );
            if (!exists) {
                generateDailySnapshot(targetDate);
            }
        }
    }

    @Override
    public void generateDailySnapshot(LocalDate date) {
        LocalDateTime startTime = date.atStartOfDay();
        LocalDateTime endTime = date.atTime(LocalTime.MAX);

        OmsDailySummary summary = new OmsDailySummary();
        summary.setRecordDate(date);

        List<AnalysisAtomicDataDTO> stats = omsOrderAnalysisMapper.getPeriodAtomicStats(startTime, endTime, "DAILY");
        BigDecimal salesAmount = BigDecimal.ZERO;
        BigDecimal profitAmount = BigDecimal.ZERO;
        int orderCount = 0;

        if (stats != null && !stats.isEmpty()) {
            AnalysisAtomicDataDTO dayStat = stats.get(0);
            salesAmount = dayStat.getNetSalesAmount() != null ? dayStat.getNetSalesAmount() : BigDecimal.ZERO;
            BigDecimal costAmount = dayStat.getCostAmount() != null ? dayStat.getCostAmount() : BigDecimal.ZERO;
            profitAmount = salesAmount.subtract(costAmount);
            orderCount = dayStat.getOrderCount() != null ? dayStat.getOrderCount() : 0;
        }

        summary.setSalesAmount(salesAmount);
        summary.setProfitAmount(profitAmount);
        summary.setOrderCount(orderCount);
        summary.setAsp(orderCount > 0 ? salesAmount.divide(new BigDecimal(orderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        Integer newMemberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM ums_member WHERE create_time >= ? AND create_time <= ?",
                Integer.class, startTime, endTime);
        summary.setNewMemberCount(newMemberCount != null ? newMemberCount : 0);

        // 🌟 修复库存 Bug：回归老办法，调用最准确的方法算成本
        BigDecimal inventoryValue = gmsGoodsService.getCurrentStockValue();
        summary.setInventoryValue(inventoryValue != null ? inventoryValue : BigDecimal.ZERO);

        OmsDailySummary exist = omsDailySummaryMapper.selectOne(
                new LambdaQueryWrapper<OmsDailySummary>().eq(OmsDailySummary::getRecordDate, date).last("LIMIT 1")
        );
        if (exist != null) {
            summary.setId(exist.getId());
            omsDailySummaryMapper.updateById(summary);
        } else {
            omsDailySummaryMapper.insert(summary);
        }
    }

    @Override
    public Map<String, Object> getTodayDashboardWithAlerts() {
        compensateSnapshots(7);
        generateDailySnapshot(LocalDate.now());

        OmsDailySummary todayStat = omsDailySummaryMapper.selectOne(
                new LambdaQueryWrapper<OmsDailySummary>().eq(OmsDailySummary::getRecordDate, LocalDate.now()).last("LIMIT 1")
        );

        Map<String, Object> avgMap = jdbcTemplate.queryForMap(
                "SELECT " +
                        "  IFNULL(AVG(sales_amount), 0) as avgSales, " +
                        "  IFNULL(AVG(order_count), 0) as avgOrders, " +
                        "  IFNULL(AVG(profit_amount), 0) as avgProfit, " +
                        "  IFNULL(AVG(asp), 0) as avgAsp " +
                        "FROM oms_daily_summary " +
                        "WHERE record_date >= ? AND record_date < ?",
                LocalDate.now().minusDays(7), LocalDate.now()
        );

        BigDecimal avgSales = new BigDecimal(avgMap.get("avgSales").toString());
        BigDecimal avgOrders = new BigDecimal(avgMap.get("avgOrders").toString());
        BigDecimal avgProfit = new BigDecimal(avgMap.get("avgProfit").toString());
        BigDecimal avgAsp = new BigDecimal(avgMap.get("avgAsp").toString());

        List<Map<String, String>> alerts = new ArrayList<>();
        BigDecimal todaySales = todayStat.getSalesAmount();
        BigDecimal todayProfit = todayStat.getProfitAmount();
        BigDecimal todayOrders = new BigDecimal(todayStat.getOrderCount());
        BigDecimal todayAsp = todayStat.getAsp();

        if (avgOrders.compareTo(BigDecimal.ZERO) > 0 && todayOrders.compareTo(avgOrders.multiply(new BigDecimal("0.8"))) < 0) {
            alerts.add(createAlert("error", "🚨 客流下滑警报", "今日订单数明显落后 7日均值，人气不旺，建议检查周边环境或启动引流品促销。"));
        }

        if (todaySales.compareTo(avgSales.multiply(new BigDecimal("0.9"))) >= 0
                && avgProfit.compareTo(BigDecimal.ZERO) > 0
                && todayProfit.compareTo(avgProfit.multiply(new BigDecimal("0.85"))) < 0) {
            alerts.add(createAlert("warning", "⚠️ 利润缩水警告", "客流营业额平稳，但净利润大幅下降，请核查是否被低毛利商品占据过多销售比重或打折过猛。"));
        }

        if (avgAsp.compareTo(BigDecimal.ZERO) > 0 && todayAsp.compareTo(avgAsp.multiply(new BigDecimal("0.85"))) < 0) {
            alerts.add(createAlert("info", "💡 客单价走低提醒", "今天顾客买得太便宜了，建议收银台引导关联加购。"));
        }

        if (alerts.isEmpty() && todaySales.compareTo(avgSales.multiply(new BigDecimal("1.2"))) > 0) {
            alerts.add(createAlert("success", "🎉 生意火爆", "今日营业额远超 7日均值，生意红火，请注意主打爆品的库存补充！"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("todayStat", todayStat);
        result.put("trendRate", calculateTrendRates(todayStat.getSalesAmount(), avgSales, new BigDecimal(todayStat.getOrderCount()), avgOrders, todayStat.getProfitAmount(), avgProfit, todayStat.getAsp(), avgAsp));
        result.put("alerts", alerts);

        return result;
    }

    @Override
    public Map<String, Object> getComprehensiveDashboard() {
        Map<String, Object> todayData = getTodayDashboardWithAlerts();

        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);

        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        LocalDateTime nextYearStart = yearStart.plusYears(1);
        LocalDateTime lastYearStart = yearStart.minusYears(1);

        Map<String, Object> thisMonth = queryActualData(monthStart, nextMonthStart);
        Map<String, Object> lastMonth = queryActualData(lastMonthStart, monthStart);

        Map<String, Object> thisYear = queryActualData(yearStart, nextYearStart);
        Map<String, Object> lastYear = queryActualData(lastYearStart, yearStart);

        Map<String, Object> totalStat = queryActualData(null, null);

        OmsDailySummary ts = (OmsDailySummary) todayData.get("todayStat");
        Map<String, String> trends = (Map<String, String>) todayData.get("trendRate");
        Map<String, Object> todayFmt = new HashMap<>();
        todayFmt.put("saleCount", ts.getSalesAmount());
        todayFmt.put("orderCount", ts.getOrderCount());
        todayFmt.put("profit", ts.getProfitAmount());
        todayFmt.put("salesTrend", trends.get("salesTrend"));
        todayFmt.put("ordersTrend", trends.get("ordersTrend"));
        todayFmt.put("profitTrend", trends.get("profitTrend"));
        todayFmt.put("aspTrend", trends.get("aspTrend"));

        Map<String, Object> monthFmt = new HashMap<>();
        attachTrends(monthFmt, thisMonth, lastMonth);

        Map<String, Object> yearFmt = new HashMap<>();
        attachTrends(yearFmt, thisYear, lastYear);

        // 🌟 修复库存 Bug：再次确保使用正确方法
        BigDecimal inventoryValue = gmsGoodsService.getCurrentStockValue();

        Map<String, Object> result = new HashMap<>();
        result.put("today", todayFmt);
        result.put("month", monthFmt);
        result.put("year", yearFmt);
        result.put("total", totalStat);
        result.put("inventoryValue", inventoryValue != null ? inventoryValue : BigDecimal.ZERO);
        result.put("alerts", todayData.get("alerts"));

        return result;
    }

    private Map<String, Object> queryActualData(LocalDateTime startTime, LocalDateTime endTime) {
        StringBuilder sql = new StringBuilder(
                "SELECT " +
                        "  COUNT(id) AS orderCount, " +
                        "  IFNULL(SUM(IFNULL(final_sales_amount, pay_amount)), 0) AS saleCount, " +
                        "  IFNULL(SUM(IFNULL(final_sales_amount, pay_amount) - IFNULL(cost_amount, 0)), 0) AS profit " +
                        "FROM oms_order " +
                        "WHERE status IN ('PAID', 'COMPLETED', 'PARTIAL_REFUNDED') ");

        List<Object> params = new ArrayList<>();
        if (startTime != null) {
            sql.append("AND create_time >= ? ");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append("AND create_time < ? ");
            params.add(endTime);
        }

        Map<String, Object> map = jdbcTemplate.queryForMap(sql.toString(), params.toArray());
        long orderCount = ((Number) map.get("orderCount")).longValue();
        BigDecimal saleCount = new BigDecimal(map.get("saleCount").toString());
        BigDecimal asp = orderCount > 0 ? saleCount.divide(new BigDecimal(orderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        map.put("asp", asp);
        return map;
    }

    private void attachTrends(Map<String, Object> target, Map<String, Object> current, Map<String, Object> previous) {
        target.put("saleCount", current.get("saleCount"));
        target.put("orderCount", current.get("orderCount"));
        target.put("profit", current.get("profit"));

        BigDecimal curSales = new BigDecimal(current.get("saleCount").toString());
        BigDecimal prevSales = new BigDecimal(previous.get("saleCount").toString());
        target.put("salesTrend", getRate(curSales, prevSales));

        BigDecimal curOrders = new BigDecimal(current.get("orderCount").toString());
        BigDecimal prevOrders = new BigDecimal(previous.get("orderCount").toString());
        target.put("ordersTrend", getRate(curOrders, prevOrders));

        BigDecimal curProfit = new BigDecimal(current.get("profit").toString());
        BigDecimal prevProfit = new BigDecimal(previous.get("profit").toString());
        target.put("profitTrend", getRate(curProfit, prevProfit));

        BigDecimal curAsp = new BigDecimal(current.get("asp").toString());
        BigDecimal prevAsp = new BigDecimal(previous.get("asp").toString());
        target.put("aspTrend", getRate(curAsp, prevAsp));
    }

    private Map<String, String> createAlert(String type, String title, String desc) {
        Map<String, String> alert = new HashMap<>();
        alert.put("type", type);
        alert.put("title", title);
        alert.put("desc", desc);
        return alert;
    }

    private Map<String, String> calculateTrendRates(BigDecimal todaySales, BigDecimal avgSales, BigDecimal todayOrders, BigDecimal avgOrders, BigDecimal todayProfit, BigDecimal avgProfit, BigDecimal todayAsp, BigDecimal avgAsp) {
        Map<String, String> rates = new HashMap<>();
        rates.put("salesTrend", getRate(todaySales, avgSales));
        rates.put("ordersTrend", getRate(todayOrders, avgOrders));
        rates.put("profitTrend", getRate(todayProfit, avgProfit));
        rates.put("aspTrend", getRate(todayAsp, avgAsp));
        return rates;
    }

    // 🌟 修复同环比算法 Bug：处理之前没数据的情况
    private String getRate(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            // 如果历史是 0，现在有收入，就算它暴涨了 100%
            return current.compareTo(BigDecimal.ZERO) > 0 ? "100.0" : "0.0";
        }
        BigDecimal rate = current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        return rate.setScale(1, RoundingMode.HALF_UP).toString();
    }
}
