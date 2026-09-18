package com.money.feature.home.application;

import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.trade.HomeDashboardOrderSnapshot;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.entity.OmsDailySummary;
import lombok.RequiredArgsConstructor;
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

/** HOME dashboard read model. This service performs no snapshot writes. */
@Service
@RequiredArgsConstructor
public class HomeDashboardQueryService {
    private final InventoryValuationQuery inventoryValuationQuery;
    private final HomeOrderReadQuery homeOrderReadQuery;
    private final HomeDailySummaryQueryService dailySummaryQueryService;

    public Map<String, Object> getTodayDashboardWithAlerts() {
        LocalDate today = LocalDate.now();
        OmsDailySummary todayStat = dailySummaryQueryService.getByDateOrEmpty(today);
        Map<String, Object> avgMap = dailySummaryQueryService.getPriorSevenDayAverages(today);
        BigDecimal avgSales = new BigDecimal(avgMap.get("avgSales").toString());
        BigDecimal avgOrders = new BigDecimal(avgMap.get("avgOrders").toString());
        BigDecimal avgProfit = new BigDecimal(avgMap.get("avgProfit").toString());
        BigDecimal avgAsp = new BigDecimal(avgMap.get("avgAsp").toString());
        List<Map<String, String>> alerts = new ArrayList<>();
        BigDecimal todaySales = todayStat.getSalesAmount();
        BigDecimal todayProfit = todayStat.getProfitAmount();
        BigDecimal todayOrders = new BigDecimal(todayStat.getOrderCount());
        BigDecimal todayAsp = todayStat.getAsp();
        if (avgOrders.compareTo(BigDecimal.ZERO) > 0 && todayOrders.compareTo(avgOrders.multiply(new BigDecimal("0.8"))) < 0) alerts.add(createAlert("error", "🚨 客流下滑警报", "今日订单数明显落后 7日均值，人气不旺，建议检查周边环境或启动引流品促销。"));
        if (todaySales.compareTo(avgSales.multiply(new BigDecimal("0.9"))) >= 0 && avgProfit.compareTo(BigDecimal.ZERO) > 0 && todayProfit.compareTo(avgProfit.multiply(new BigDecimal("0.85"))) < 0) alerts.add(createAlert("warning", "⚠️ 利润缩水警告", "客流营业额平稳，但净利润大幅下降，请核查是否被低毛利商品占据过多销售比重或打折过猛。"));
        if (avgAsp.compareTo(BigDecimal.ZERO) > 0 && todayAsp.compareTo(avgAsp.multiply(new BigDecimal("0.85"))) < 0) alerts.add(createAlert("info", "💡 客单价走低提醒", "今天顾客买得太便宜了，建议收银台引导关联加购。"));
        if (alerts.isEmpty() && todaySales.compareTo(avgSales.multiply(new BigDecimal("1.2"))) > 0) alerts.add(createAlert("success", "🎉 生意火爆", "今日营业额远超 7日均值，生意红火，请注意主打爆品的库存补充！"));
        Map<String, Object> result = new HashMap<>();
        result.put("todayStat", todayStat);
        result.put("trendRate", calculateTrendRates(todaySales, avgSales, todayOrders, avgOrders, todayProfit, avgProfit, todayAsp, avgAsp));
        result.put("alerts", alerts);
        return result;
    }

    public Map<String, Object> getComprehensiveDashboard() {
        Map<String, Object> todayData = getTodayDashboardWithAlerts();
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        Map<String, Object> thisMonth = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(monthStart, monthStart.plusMonths(1)));
        Map<String, Object> lastMonth = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(monthStart.minusMonths(1), monthStart));
        Map<String, Object> thisYear = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(yearStart, yearStart.plusYears(1)));
        Map<String, Object> lastYear = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(yearStart.minusYears(1), yearStart));
        Map<String, Object> totalStat = toDashboardMap(homeOrderReadQuery.summarizeDashboardRange(null, null));
        OmsDailySummary snapshot = (OmsDailySummary) todayData.get("todayStat");
        Map<String, String> trends = (Map<String, String>) todayData.get("trendRate");
        Map<String, Object> today = new HashMap<>();
        today.put("saleCount", snapshot.getSalesAmount()); today.put("orderCount", snapshot.getOrderCount()); today.put("profit", snapshot.getProfitAmount());
        today.put("salesTrend", trends.get("salesTrend")); today.put("ordersTrend", trends.get("ordersTrend")); today.put("profitTrend", trends.get("profitTrend")); today.put("aspTrend", trends.get("aspTrend"));
        Map<String, Object> month = new HashMap<>(); attachTrends(month, thisMonth, lastMonth);
        Map<String, Object> year = new HashMap<>(); attachTrends(year, thisYear, lastYear);
        BigDecimal inventoryValue = inventoryValuationQuery.getCurrentStockValue();
        Map<String, Object> result = new HashMap<>();
        result.put("today", today); result.put("month", month); result.put("year", year); result.put("total", totalStat);
        result.put("inventoryValue", inventoryValue != null ? inventoryValue : BigDecimal.ZERO); result.put("alerts", todayData.get("alerts"));
        return result;
    }

    private Map<String, Object> toDashboardMap(HomeDashboardOrderSnapshot snapshot) {
        Map<String, Object> result = new HashMap<>(); result.put("orderCount", snapshot.getOrderCount()); result.put("saleCount", snapshot.getSaleCount()); result.put("profit", snapshot.getProfit());
        result.put("asp", snapshot.getOrderCount() > 0 ? snapshot.getSaleCount().divide(new BigDecimal(snapshot.getOrderCount()), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO); return result;
    }
    private void attachTrends(Map<String, Object> target, Map<String, Object> current, Map<String, Object> previous) {
        target.put("saleCount", current.get("saleCount")); target.put("orderCount", current.get("orderCount")); target.put("profit", current.get("profit"));
        target.put("salesTrend", getRate(new BigDecimal(current.get("saleCount").toString()), new BigDecimal(previous.get("saleCount").toString()))); target.put("ordersTrend", getRate(new BigDecimal(current.get("orderCount").toString()), new BigDecimal(previous.get("orderCount").toString()))); target.put("profitTrend", getRate(new BigDecimal(current.get("profit").toString()), new BigDecimal(previous.get("profit").toString()))); target.put("aspTrend", getRate(new BigDecimal(current.get("asp").toString()), new BigDecimal(previous.get("asp").toString())));
    }
    private Map<String, String> createAlert(String type, String title, String desc) { Map<String, String> alert = new HashMap<>(); alert.put("type", type); alert.put("title", title); alert.put("desc", desc); return alert; }
    private Map<String, String> calculateTrendRates(BigDecimal todaySales, BigDecimal avgSales, BigDecimal todayOrders, BigDecimal avgOrders, BigDecimal todayProfit, BigDecimal avgProfit, BigDecimal todayAsp, BigDecimal avgAsp) { Map<String, String> rates = new HashMap<>(); rates.put("salesTrend", getRate(todaySales, avgSales)); rates.put("ordersTrend", getRate(todayOrders, avgOrders)); rates.put("profitTrend", getRate(todayProfit, avgProfit)); rates.put("aspTrend", getRate(todayAsp, avgAsp)); return rates; }
    private String getRate(BigDecimal current, BigDecimal previous) { if (previous.compareTo(BigDecimal.ZERO) == 0) return current.compareTo(BigDecimal.ZERO) > 0 ? "100.0" : "0.0"; return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP).toString(); }
}
