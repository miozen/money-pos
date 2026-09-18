package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.member.HomeDailyMemberQuery;
import com.money.contract.trade.HomeDailyOrderSnapshot;
import com.money.contract.trade.HomeDashboardOrderSnapshot;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final JdbcTemplate jdbcTemplate;
    private final InventoryValuationQuery inventoryValuationQuery;
    private final HomeOrderReadQuery homeOrderReadQuery;
    private final HomeDailyMemberQuery homeDailyMemberQuery;

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
        LocalDateTime endTime = date.atTime(java.time.LocalTime.MAX);

        OmsDailySummary summary = new OmsDailySummary();
        summary.setRecordDate(date);

        HomeDailyOrderSnapshot orderSnapshot = homeOrderReadQuery.summarizeDailySnapshot(date);
        BigDecimal salesAmount = orderSnapshot.getSalesAmount();
        BigDecimal profitAmount = salesAmount.subtract(orderSnapshot.getCostAmount());
        int orderCount = orderSnapshot.getOrderCount();

        summary.setSalesAmount(salesAmount);
        summary.setProfitAmount(profitAmount);
        summary.setOrderCount(orderCount);
        summary.setAsp(orderCount > 0 ? salesAmount.divide(new BigDecimal(orderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        summary.setNewMemberCount(homeDailyMemberQuery.countNewMembers(date));

        BigDecimal inventoryValue = inventoryValuationQuery.getCurrentStockValue();
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

        Map<String, Object> thisMonth = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(monthStart, nextMonthStart));
        Map<String, Object> lastMonth = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(lastMonthStart, monthStart));

        Map<String, Object> thisYear = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(yearStart, nextYearStart));
        Map<String, Object> lastYear = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(lastYearStart, yearStart));

        Map<String, Object> totalStat = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(null, null));

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

        BigDecimal inventoryValue = inventoryValuationQuery.getCurrentStockValue();

        Map<String, Object> result = new HashMap<>();
        result.put("today", todayFmt);
        result.put("month", monthFmt);
        result.put("year", yearFmt);
        result.put("total", totalStat);
        result.put("inventoryValue", inventoryValue != null ? inventoryValue : BigDecimal.ZERO);
        result.put("alerts", todayData.get("alerts"));

        return result;
    }

    private Map<String, Object> toDashboardMap(HomeDashboardOrderSnapshot snapshot) {
        Map<String, Object> result = new HashMap<>();
        result.put("orderCount", snapshot.getOrderCount());
        result.put("saleCount", snapshot.getSaleCount());
        result.put("profit", snapshot.getProfit());
        result.put("asp", snapshot.getOrderCount() > 0
                ? snapshot.getSaleCount().divide(new BigDecimal(snapshot.getOrderCount()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return result;
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
