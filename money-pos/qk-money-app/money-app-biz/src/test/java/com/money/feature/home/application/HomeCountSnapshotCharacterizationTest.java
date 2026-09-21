package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.member.HomeDailyMemberQuery;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.contract.trade.HomeOrderReadSnapshot;
import com.money.contract.trade.HomeDailyOrderSnapshot;
import com.money.contract.trade.HomeDashboardOrderSnapshot;
import com.money.contract.trade.HomeSalesTrendSnapshot;
import com.money.contract.trade.HomeBrandSalesSnapshot;
import com.money.dto.Home.HomeCountVO;
import com.money.dto.Home.HomeChartsVO;
import com.money.dto.Home.TrendChartVO;
import com.money.dto.Home.BrandPieVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.entity.OmsOrder;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.feature.home.application.HomeService;
import com.money.feature.home.interfaces.rest.HomeController;
import com.money.feature.home.infrastructure.persistence.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.support.TradeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterizes the existing write-on-read behavior of GET /home/count before HOME types move.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class HomeCountSnapshotCharacterizationTest {

    @Autowired
    private HomeController homeController;

    @Autowired
    private OmsDailySummaryMapper dailySummaryMapper;
    @Autowired
    private InventoryValuationQuery inventoryValuationQuery;
    @Autowired
    private HomeService homeService;
    @Autowired
    private HomeOrderReadQuery homeOrderReadQuery;
    @Autowired
    private OmsOrderMapper omsOrderMapper;
    @Autowired
    private DecisionEngineService decisionEngineService;
    @Autowired
    private HomeDailySnapshotRefreshTask dailySnapshotRefreshTask;
    @Autowired
    private OmsOrderDetailMapper omsOrderDetailMapper;
    @Autowired
    private HomeDailyMemberQuery homeDailyMemberQuery;
    @Autowired
    private UmsMemberMapper umsMemberMapper;
    @Autowired
    private TradeFixture tradeFixture;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void scheduledRefreshCreatesAndUpdatesTodaySnapshotWhileHomeCountStaysReadOnly() {
        LocalDate today = LocalDate.now();
        dailySummaryMapper.delete(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, today));

        Map<String, Object> firstResponse = homeController.homeCountVO();
        BigDecimal expectedInventoryValue = inventoryValuationQuery.getCurrentStockValue();

        assertThat(firstResponse).containsOnlyKeys("today", "month", "year", "total", "inventoryValue", "alerts");
        assertThat(firstResponse.get("today")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("month")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("year")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("total")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("alerts")).isInstanceOf(java.util.List.class);

        assertThat(todaySnapshot(today)).isNull();
        assertThat((BigDecimal) firstResponse.get("inventoryValue")).isEqualByComparingTo(expectedInventoryValue);
        RequestContextHolder.resetRequestAttributes();
        dailySnapshotRefreshTask.refreshSnapshots("test initial refresh");
        authenticateTenant();
        OmsDailySummary firstSnapshot = todaySnapshot(today);
        assertThat(firstSnapshot).isNotNull();
        assertThat(firstSnapshot.getInventoryValue()).isEqualByComparingTo(expectedInventoryValue);
        HomeCountVO homeCount = homeService.homeCount();
        assertThat(homeCount.getInventoryValue()).isEqualByComparingTo(expectedInventoryValue);
        Long snapshotId = firstSnapshot.getId();

        firstSnapshot.setSalesAmount(new BigDecimal("-1.00"));
        firstSnapshot.setMemberRecharge(new BigDecimal("19.00"));
        dailySummaryMapper.updateById(firstSnapshot);

        Map<String, Object> secondResponse = homeController.homeCountVO();
        assertThat(todaySnapshot(today).getSalesAmount()).isEqualByComparingTo("-1.00");
        dailySnapshotRefreshTask.refreshSnapshots("test update refresh");
        OmsDailySummary updatedSnapshot = todaySnapshot(today);

        assertThat(secondResponse).containsOnlyKeys("today", "month", "year", "total", "inventoryValue", "alerts");
        assertThat(updatedSnapshot.getId()).isEqualTo(snapshotId);
        assertThat(updatedSnapshot.getSalesAmount()).isNotEqualByComparingTo("-1.00");
        assertThat(updatedSnapshot.getMemberRecharge()).isEqualByComparingTo("19.00");
        assertThat(dailySummaryMapper.selectCount(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, today))).isEqualTo(1);
    }

    @Test
    void homeCountCompensatesOnlyThePreviousSevenMissingDatesAndAlwaysRegeneratesToday() {
        LocalDate today = LocalDate.now();
        LocalDate oldestCompensatedDate = today.minusDays(7);
        LocalDate outsideCompensationWindow = today.minusDays(8);
        dailySummaryMapper.delete(new LambdaQueryWrapper<OmsDailySummary>()
                .in(OmsDailySummary::getRecordDate, today, oldestCompensatedDate, outsideCompensationWindow));

        OmsDailySummary outsideSnapshot = new OmsDailySummary();
        outsideSnapshot.setRecordDate(outsideCompensationWindow);
        outsideSnapshot.setSalesAmount(new BigDecimal("123.45"));
        outsideSnapshot.setOrderCount(12);
        outsideSnapshot.setProfitAmount(new BigDecimal("23.45"));
        outsideSnapshot.setAsp(new BigDecimal("10.29"));
        outsideSnapshot.setInventoryValue(BigDecimal.ZERO);
        outsideSnapshot.setMemberRecharge(BigDecimal.ZERO);
        outsideSnapshot.setNewMemberCount(0);
        dailySummaryMapper.insert(outsideSnapshot);
        Long outsideSnapshotId = outsideSnapshot.getId();

        homeController.homeCountVO();
        assertThat(snapshotCount(oldestCompensatedDate)).isZero();
        dailySnapshotRefreshTask.refreshSnapshots("test compensation refresh");

        assertThat(snapshotCount(oldestCompensatedDate)).isEqualTo(1);
        assertThat(snapshotCount(today)).isEqualTo(1);
        OmsDailySummary preservedOutsideSnapshot = todaySnapshot(outsideCompensationWindow);
        assertThat(preservedOutsideSnapshot.getId()).isEqualTo(outsideSnapshotId);
        assertThat(preservedOutsideSnapshot.getSalesAmount()).isEqualByComparingTo("123.45");
    }

    @Test
    void homeCountContractKeepsFinancialStatusesAmountsAndRightOpenTimeRange() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        HomeOrderReadSnapshot beforeToday = homeOrderReadQuery.summarizeHomeCount(todayStart, tomorrowStart);
        HomeOrderReadSnapshot beforeTotal = homeOrderReadQuery.summarizeHomeCount(null, null);

        insertOrder("PAID", new BigDecimal("10.00"), new BigDecimal("4.00"), todayStart.plusHours(1));
        insertOrder("REFUNDED", new BigDecimal("3.00"), BigDecimal.ZERO, todayStart.plusHours(2));
        insertOrder("CLOSED", new BigDecimal("99.00"), new BigDecimal("1.00"), todayStart.plusHours(3));
        insertOrder("PAID", new BigDecimal("7.00"), new BigDecimal("2.00"), tomorrowStart);

        HomeOrderReadSnapshot today = homeOrderReadQuery.summarizeHomeCount(todayStart, tomorrowStart);
        HomeOrderReadSnapshot total = homeOrderReadQuery.summarizeHomeCount(null, null);
        HomeCountVO homeCount = homeService.homeCount();

        assertThat(today.getOrderCount() - beforeToday.getOrderCount()).isEqualTo(2L);
        assertThat(today.getSaleCount().subtract(beforeToday.getSaleCount())).isEqualByComparingTo("13.00");
        assertThat(today.getCostCount().subtract(beforeToday.getCostCount())).isEqualByComparingTo("4.00");
        assertThat(today.getProfit().subtract(beforeToday.getProfit())).isEqualByComparingTo("9.00");
        assertThat(total.getOrderCount() - beforeTotal.getOrderCount()).isEqualTo(3L);
        assertThat(total.getSaleCount().subtract(beforeTotal.getSaleCount())).isEqualByComparingTo("20.00");
        assertThat(total.getCostCount().subtract(beforeTotal.getCostCount())).isEqualByComparingTo("6.00");
        assertThat(total.getProfit().subtract(beforeTotal.getProfit())).isEqualByComparingTo("14.00");
        assertOrderCount(homeCount.getToday(), today);
        assertOrderCount(homeCount.getTotal(), total);

        HomeOrderReadSnapshot empty = homeOrderReadQuery.summarizeHomeCount(
                LocalDate.of(2099, 1, 1).atStartOfDay(), LocalDate.of(2099, 1, 2).atStartOfDay());
        assertThat(empty.getOrderCount()).isZero();
        assertThat(empty.getSaleCount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.getCostCount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.getProfit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void dailySnapshotAndComprehensiveDashboardKeepTheirSeparateTradeOrderRules() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        HomeDailyOrderSnapshot beforeDaily = homeOrderReadQuery.summarizeDailySnapshot(today);
        HomeDashboardOrderSnapshot beforeDashboard = homeOrderReadQuery.summarizeDashboardRange(todayStart, tomorrowStart);

        insertOrder("PAID", new BigDecimal("10.00"), new BigDecimal("4.00"), todayStart.plusHours(4));
        insertOrder("PARTIAL_REFUNDED", new BigDecimal("6.00"), new BigDecimal("1.00"), todayStart.plusHours(5));
        insertOrder("REFUNDED", new BigDecimal("3.00"), BigDecimal.ZERO, todayStart.plusHours(6));
        insertOrder("COMPLETED", new BigDecimal("8.00"), new BigDecimal("2.00"), todayStart.plusHours(7));
        insertOrder("CLOSED", new BigDecimal("99.00"), new BigDecimal("1.00"), todayStart.plusHours(8));

        HomeDailyOrderSnapshot daily = homeOrderReadQuery.summarizeDailySnapshot(today);
        HomeDashboardOrderSnapshot dashboard = homeOrderReadQuery.summarizeDashboardRange(todayStart, tomorrowStart);
        decisionEngineService.generateDailySnapshot(today);
        OmsDailySummary storedSnapshot = todaySnapshot(today);
        Map<String, Object> response = decisionEngineService.getComprehensiveDashboard();
        Map<String, Object> month = (Map<String, Object>) response.get("month");

        assertThat(daily.getOrderCount() - beforeDaily.getOrderCount()).isEqualTo(2);
        assertThat(daily.getSalesAmount().subtract(beforeDaily.getSalesAmount())).isEqualByComparingTo("16.00");
        assertThat(daily.getCostAmount().subtract(beforeDaily.getCostAmount())).isEqualByComparingTo("5.00");
        assertThat(dashboard.getOrderCount() - beforeDashboard.getOrderCount()).isEqualTo(3L);
        assertThat(dashboard.getSaleCount().subtract(beforeDashboard.getSaleCount())).isEqualByComparingTo("24.00");
        assertThat(dashboard.getProfit().subtract(beforeDashboard.getProfit())).isEqualByComparingTo("17.00");
        assertThat(storedSnapshot.getOrderCount()).isEqualTo(daily.getOrderCount());
        assertThat(storedSnapshot.getSalesAmount()).isEqualByComparingTo(daily.getSalesAmount());
        assertThat(storedSnapshot.getProfitAmount()).isEqualByComparingTo(daily.getSalesAmount().subtract(daily.getCostAmount()));

        HomeDashboardOrderSnapshot monthSnapshot = homeOrderReadQuery.summarizeDashboardRange(
                java.time.YearMonth.now().atDay(1).atStartOfDay(), java.time.YearMonth.now().atDay(1).atStartOfDay().plusMonths(1));
        assertThat(month.get("orderCount")).isEqualTo(monthSnapshot.getOrderCount());
        assertThat((BigDecimal) month.get("saleCount")).isEqualByComparingTo(monthSnapshot.getSaleCount());
        assertThat((BigDecimal) month.get("profit")).isEqualByComparingTo(monthSnapshot.getProfit());
    }

    @Test
    void dailySnapshotUsesUmsOwnedNewMemberCount() {
        LocalDate today = LocalDate.now();
        int before = homeDailyMemberQuery.countNewMembers(today);
        com.money.entity.UmsMember member = tradeFixture.createMember("home" + (System.nanoTime() % 1_000_000),
                BigDecimal.ZERO);
        member.setCreateTime(today.atTime(12, 0));
        umsMemberMapper.updateById(member);

        assertThat(homeDailyMemberQuery.countNewMembers(today)).isEqualTo(before + 1);
        decisionEngineService.generateDailySnapshot(today);
        assertThat(todaySnapshot(today).getNewMemberCount()).isEqualTo(before + 1);
    }

    @Test
    void chartsKeepTradeTrendAndBrandRulesAcrossAllTimeRanges() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDayStart = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
        BigDecimal trendBefore = trendAmountForDate(homeOrderReadQuery.listSalesTrend(sevenDayStart, null), LocalDate.now());
        BigDecimal brandBefore = brandAmount(homeOrderReadQuery.listBrandSales(todayStart, null), "无品牌/未知");
        String orderNo = insertOrder("PAID", new BigDecimal("20.00"), new BigDecimal("8.00"), todayStart.plusHours(9));
        insertOrderDetail(orderNo, "PAID", 2, 1, new BigDecimal("10.00"));

        assertThat(trendAmountForDate(homeOrderReadQuery.listSalesTrend(sevenDayStart, null), LocalDate.now()).subtract(trendBefore))
                .isEqualByComparingTo("20.00");
        assertThat(brandAmount(homeOrderReadQuery.listBrandSales(todayStart, null), "无品牌/未知").subtract(brandBefore))
                .isEqualByComparingTo("10.00");

        assertChartsMatchTradeQuery("today", sevenDayStart, null, todayStart, null);
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        assertChartsMatchTradeQuery("month", monthStart, monthStart.plusMonths(1), monthStart, monthStart.plusMonths(1));
        LocalDateTime yearStart = Year.now().atDay(1).atStartOfDay();
        assertChartsMatchTradeQuery("year", yearStart, yearStart.plusYears(1), yearStart, yearStart.plusYears(1));
        assertChartsMatchTradeQuery("total", null, null, null, null);
    }

    private void assertChartsMatchTradeQuery(String timeRange, LocalDateTime trendStart, LocalDateTime trendEnd,
                                              LocalDateTime brandStart, LocalDateTime brandEnd) {
        HomeChartsVO charts = homeController.getChartsData(timeRange);
        List<HomeSalesTrendSnapshot> trendSnapshots = homeOrderReadQuery.listSalesTrend(trendStart, trendEnd);
        List<HomeBrandSalesSnapshot> brandSnapshots = homeOrderReadQuery.listBrandSales(brandStart, brandEnd);
        assertThat(charts.getTrendData()).hasSameSizeAs(trendSnapshots);
        assertThat(charts.getPieData()).hasSameSizeAs(brandSnapshots);
        for (int i = 0; i < trendSnapshots.size(); i++) {
            TrendChartVO actual = charts.getTrendData().get(i);
            HomeSalesTrendSnapshot expected = trendSnapshots.get(i);
            assertThat(actual.getDate()).isEqualTo(expected.getDate());
            assertThat(actual.getSales()).isEqualByComparingTo(expected.getSales());
            assertThat(actual.getProfit()).isEqualByComparingTo(expected.getProfit());
        }
        for (int i = 0; i < brandSnapshots.size(); i++) {
            BrandPieVO actual = charts.getPieData().get(i);
            HomeBrandSalesSnapshot expected = brandSnapshots.get(i);
            assertThat(actual.getName()).isEqualTo(expected.getName());
            assertThat(actual.getValue()).isEqualByComparingTo(expected.getValue());
        }
    }

    private BigDecimal trendAmountForDate(List<HomeSalesTrendSnapshot> snapshots, LocalDate date) {
        String dateLabel = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return snapshots.stream().filter(snapshot -> dateLabel.equals(snapshot.getDate()))
                .map(HomeSalesTrendSnapshot::getSales).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal brandAmount(List<HomeBrandSalesSnapshot> snapshots, String brandName) {
        return snapshots.stream().filter(snapshot -> brandName.equals(snapshot.getName()))
                .map(HomeBrandSalesSnapshot::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertOrderCount(OrderCountVO actual, HomeOrderReadSnapshot expected) {
        assertThat(actual.getOrderCount()).isEqualTo(expected.getOrderCount());
        assertThat(actual.getSaleCount()).isEqualByComparingTo(expected.getSaleCount());
        assertThat(actual.getCostCount()).isEqualByComparingTo(expected.getCostCount());
        assertThat(actual.getProfit()).isEqualByComparingTo(expected.getProfit());
    }

    private String insertOrder(String status, BigDecimal finalSalesAmount, BigDecimal costAmount, LocalDateTime createTime) {
        OmsOrder order = new OmsOrder();
        String orderNo = "HOME-P2-" + System.nanoTime();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setVip(false);
        order.setFinalSalesAmount(finalSalesAmount);
        order.setPayAmount(new BigDecimal("3.00"));
        order.setCostAmount(costAmount);
        order.setTotalAmount(finalSalesAmount == null ? new BigDecimal("3.00") : finalSalesAmount);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setPaymentTime(createTime);
        order.setTenantId(0L);
        order.setCreateTime(createTime);
        omsOrderMapper.insert(order);
        return orderNo;
    }

    private void insertOrderDetail(String orderNo, String status, int quantity, int returnQuantity, BigDecimal goodsPrice) {
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderNo(orderNo);
        detail.setStatus(status);
        detail.setGoodsId(System.nanoTime());
        detail.setGoodsBarcode("HOME-CHART-" + System.nanoTime());
        detail.setGoodsName("HOME chart fixture");
        detail.setGoodsPrice(goodsPrice);
        detail.setQuantity(quantity);
        detail.setSalePrice(goodsPrice);
        detail.setPurchasePrice(new BigDecimal("4.00"));
        detail.setVipPrice(goodsPrice);
        detail.setCoupon(BigDecimal.ZERO);
        detail.setReturnQuantity(returnQuantity);
        detail.setTenantId(0L);
        omsOrderDetailMapper.insert(detail);
    }

    private OmsDailySummary todaySnapshot(LocalDate date) {
        return dailySummaryMapper.selectOne(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, date)
                .last("LIMIT 1"));
    }

    private long snapshotCount(LocalDate date) {
        return dailySummaryMapper.selectCount(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, date));
    }
}
