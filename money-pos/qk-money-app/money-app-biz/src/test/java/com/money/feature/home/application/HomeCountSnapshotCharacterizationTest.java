package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.InventoryValuationQuery;
import com.money.contract.trade.HomeOrderReadQuery;
import com.money.contract.trade.HomeOrderReadSnapshot;
import com.money.contract.trade.HomeDailyOrderSnapshot;
import com.money.contract.trade.HomeDashboardOrderSnapshot;
import com.money.dto.Home.HomeCountVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.entity.OmsOrder;
import com.money.feature.home.application.HomeService;
import com.money.feature.home.interfaces.rest.HomeController;
import com.money.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
import com.money.mapper.OmsOrderMapper;
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
    void homeCountCreatesThenUpdatesTodaySnapshotAndKeepsDashboardShape() {
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

        OmsDailySummary firstSnapshot = todaySnapshot(today);
        assertThat(firstSnapshot).isNotNull();
        assertThat((BigDecimal) firstResponse.get("inventoryValue")).isEqualByComparingTo(expectedInventoryValue);
        assertThat(firstSnapshot.getInventoryValue()).isEqualByComparingTo(expectedInventoryValue);
        HomeCountVO homeCount = homeService.homeCount();
        assertThat(homeCount.getInventoryValue()).isEqualByComparingTo(expectedInventoryValue);
        Long snapshotId = firstSnapshot.getId();

        firstSnapshot.setSalesAmount(new BigDecimal("-1.00"));
        dailySummaryMapper.updateById(firstSnapshot);

        Map<String, Object> secondResponse = homeController.homeCountVO();
        OmsDailySummary updatedSnapshot = todaySnapshot(today);

        assertThat(secondResponse).containsOnlyKeys("today", "month", "year", "total", "inventoryValue", "alerts");
        assertThat(updatedSnapshot.getId()).isEqualTo(snapshotId);
        assertThat(updatedSnapshot.getSalesAmount()).isNotEqualByComparingTo("-1.00");
        assertThat(dailySummaryMapper.selectCount(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, today))).isEqualTo(1);
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

    private void assertOrderCount(OrderCountVO actual, HomeOrderReadSnapshot expected) {
        assertThat(actual.getOrderCount()).isEqualTo(expected.getOrderCount());
        assertThat(actual.getSaleCount()).isEqualByComparingTo(expected.getSaleCount());
        assertThat(actual.getCostCount()).isEqualByComparingTo(expected.getCostCount());
        assertThat(actual.getProfit()).isEqualByComparingTo(expected.getProfit());
    }

    private void insertOrder(String status, BigDecimal finalSalesAmount, BigDecimal costAmount, LocalDateTime createTime) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo("HOME-P2-" + System.nanoTime());
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
    }

    private OmsDailySummary todaySnapshot(LocalDate date) {
        return dailySummaryMapper.selectOne(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, date)
                .last("LIMIT 1"));
    }
}
