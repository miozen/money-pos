package com.money.feature.fin.application;

import com.money.feature.fin.application.dashboard.FinanceDashboardService;
import com.money.feature.fin.application.report.FinanceReportService;
import com.money.feature.fin.application.report.FinanceShiftService;
import com.money.feature.fin.application.analysis.FinanceRiskService;
import com.money.feature.fin.application.analysis.FinanceProfitService;
import com.money.contract.member.FinanceMemberAssetCompositionSnapshot;
import com.money.contract.member.FinanceMemberAssetQuery;
import com.money.contract.trade.FinanceRiskQuery;
import com.money.contract.trade.FinanceOperatingAnalysisQuery;
import com.money.contract.trade.FinanceOperatingMetricSnapshot;
import com.money.contract.trade.FinanceSalesDashboardQuery;
import com.money.contract.trade.FinanceTrafficQuery;
import com.money.contract.trade.FinanceProductAnalysisQuery;
import com.money.contract.system.FinanceTrafficStrategyQuery;
import com.money.feature.fin.application.analysis.OmsSalesAnalysisService;
import com.money.dto.Finance.FinanceDataVO.FinanceDashboardVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.PerformanceReportVO;
import com.money.dto.OmsOrder.OmsSalesDataVO.SalesDashboardVO;
import com.money.dto.OmsOrder.OrderCountVO;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.GmsBrand;
import com.money.entity.GmsGoodsCategory;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.OmsOrderPay;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.entity.SysStrategy;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.GmsBrandMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.SysStrategyMapper;
import com.money.support.TradeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class FinanceFeatureIntegrationTest {

    @Autowired
    private FinanceDashboardService financeDashboardService;

    @Autowired
    private FinanceReportService financeReportService;

    @Autowired
    private FinanceShiftService financeShiftService;
    @Autowired
    private FinanceRiskService financeRiskService;
    @Autowired
    private FinanceProfitService financeProfitService;
    @Autowired
    private FinanceRiskQuery financeRiskQuery;
    @Autowired
    private FinanceOperatingAnalysisQuery financeOperatingAnalysisQuery;
    @Autowired
    private FinanceSalesDashboardQuery financeSalesDashboardQuery;
    @Autowired
    private FinanceTrafficQuery financeTrafficQuery;
    @Autowired
    private FinanceProductAnalysisQuery financeProductAnalysisQuery;
    @Autowired
    private FinanceTrafficStrategyQuery financeTrafficStrategyQuery;
    @Autowired
    private OmsSalesAnalysisService salesAnalysisService;

    @Autowired
    private GmsInventoryDocMapper inventoryDocMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderPayMapper orderPayMapper;
    @Autowired
    private OmsOrderDetailMapper orderDetailMapper;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private GmsGoodsCategoryMapper goodsCategoryMapper;
    @Autowired
    private FinanceMemberAssetQuery financeMemberAssetQuery;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberLogMapper memberLogMapper;
    @Autowired
    private SysStrategyMapper strategyMapper;
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
    void financeFeatureServicesReadFromMovedMapperAndSharedReadModels() {
        LocalDate today = LocalDate.now();
        assertThatCode(() -> {
            financeDashboardService.getDashboardData(today.toString());
            financeDashboardService.getChannelMixAnalysis(today.toString(), today.toString());
            financeDashboardService.getAssetDashboard();
            financeReportService.getDailyWaterfallReport(null);
            financeShiftService.getShiftHandover(
                    LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), null
            );
        }).doesNotThrowAnyException();
    }

    @Test
    void dashboardUsesGmsFinancialDocumentSnapshotAndOnlySubtractsNegativeAmounts() {
        LocalDate today = LocalDate.now();
        FinanceDashboardVO before = financeDashboardService.getDashboardData(today.toString());
        insertInventoryDocument("FIN-P2-NEG-" + System.nanoTime(), "OUTBOUND", new java.math.BigDecimal("-5.50"));
        insertInventoryDocument("FIN-P2-POS-" + System.nanoTime(), "CHECK", new java.math.BigDecimal("3.25"));
        insertInventoryDocument("FIN-P2-IGN-" + System.nanoTime(), "INBOUND", new java.math.BigDecimal("99.00"));

        FinanceDashboardVO dashboard = financeDashboardService.getDashboardData(today.toString());

        assertThat(dashboard.getGrossProfit()).isEqualByComparingTo(before.getGrossProfit().subtract(new java.math.BigDecimal("5.50")));
    }

    @Test
    void dashboardUsesUmsMemberAssetSnapshotsForRechargeDebtAndAssetComposition() {
        LocalDate today = LocalDate.now();
        FinanceDashboardVO before = financeDashboardService.getDashboardData(today.toString());
        FinanceMemberAssetCompositionSnapshot beforeComposition = financeMemberAssetQuery.getAssetComposition();

        UmsMember member = tradeFixture.createMember("f" + (System.nanoTime() % 1_000_000), BigDecimal.ZERO);
        member.setBalance(new BigDecimal("17.00"));
        member.setCoupon(new BigDecimal("3.00"));
        memberMapper.updateById(member);
        insertMemberLog(member.getId(), "RECHARGE", new BigDecimal("11.00"));
        insertMemberLog(member.getId(), "REVERSAL", new BigDecimal("-2.00"));

        FinanceDashboardVO dashboard = financeDashboardService.getDashboardData(today.toString());
        FinanceMemberAssetCompositionSnapshot composition = financeMemberAssetQuery.getAssetComposition();

        assertThat(dashboard.getExternalIncome()).isEqualByComparingTo(before.getExternalIncome().add(new BigDecimal("9.00")));
        assertThat(dashboard.getTotalDebt()).isEqualByComparingTo(before.getTotalDebt().add(new BigDecimal("17.00")));
        assertThat(dashboard.getTrendRecharge().get(dashboard.getTrendRecharge().size() - 1))
                .isEqualByComparingTo(before.getTrendRecharge().get(before.getTrendRecharge().size() - 1).add(new BigDecimal("9.00")));
        assertThat(composition.getPrincipalAmount())
                .isEqualByComparingTo(beforeComposition.getPrincipalAmount().add(new BigDecimal("17.00")));
        assertThat(composition.getGiftAmount())
                .isEqualByComparingTo(beforeComposition.getGiftAmount().add(new BigDecimal("3.00")));

        BigDecimal totalAssets = composition.getPrincipalAmount().add(composition.getGiftAmount());
        assertThat(financeDashboardService.getAssetDashboard().getPrincipalRatio())
                .isEqualByComparingTo(composition.getPrincipalAmount().multiply(new BigDecimal("100"))
                        .divide(totalAssets, 2, RoundingMode.HALF_UP));
    }

    @Test
    void dashboardUsesTradeOrderPaymentSnapshotsForMetricsChannelsAndRefundTrend() {
        LocalDate today = LocalDate.now();
        FinanceDashboardVO before = financeDashboardService.getDashboardData(today.toString());
        String suffix = "F" + (System.nanoTime() % 1_000_000_000L);
        insertOrder(suffix + "-PAID", "PAID", new BigDecimal("20.00"), new BigDecimal("18.00"),
                new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        insertPayment(suffix + "-PAID", "CASH", null, new BigDecimal("18.00"));
        insertOrder(suffix + "-REFUND", "REFUNDED", new BigDecimal("10.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        insertPayment(suffix + "-REFUND", "AGGREGATE", "WX", new BigDecimal("10.00"));

        FinanceDashboardVO dashboard = financeDashboardService.getDashboardData(today.toString());
        assertThat(dashboard.getTotalAmount()).isEqualByComparingTo(before.getTotalAmount().add(new BigDecimal("30.00")));
        assertThat(dashboard.getPayAmount()).isEqualByComparingTo(before.getPayAmount().add(new BigDecimal("20.00")));
        assertThat(dashboard.getRefundAmount()).isEqualByComparingTo(before.getRefundAmount().add(new BigDecimal("2.00")));
        assertThat(dashboard.getTrendRefund().get(6)).isEqualByComparingTo(before.getTrendRefund().get(6).add(new BigDecimal("2.00")));
        assertThat(dashboard.getTrendCash().get(6)).isEqualByComparingTo(before.getTrendCash().get(6).add(new BigDecimal("18.00")));

        com.money.dto.Finance.FinanceDataVO.ChannelMixAnalysisVO channelMix =
                financeDashboardService.getChannelMixAnalysis(today.toString(), today.toString());
        assertThat(channelMix.getCashList().get(0)).isGreaterThanOrEqualTo(new BigDecimal("18.00"));
        assertThat(channelMix.getCouponList().get(0)).isGreaterThanOrEqualTo(new BigDecimal("2.00"));
        assertThat(channelMix.getVoucherList().get(0)).isGreaterThanOrEqualTo(new BigDecimal("3.00"));
        assertThat(financeDashboardService.getAssetDashboard().getTodayRealCash()).isGreaterThanOrEqualTo(new BigDecimal("18.00"));
    }

    @Test
    void riskControlUsesTradeAuditSnapshotsAndPreservesCardsAndRows() {
        String suffix = "R" + (System.nanoTime() % 1_000_000_000L);
        String cashier = "risk-" + suffix;
        insertRiskOrder(suffix + "-LOSS", cashier, "PAID", new BigDecimal("30.00"),
                new BigDecimal("30.00"), new BigDecimal("40.00"), BigDecimal.ZERO);
        insertRiskOrder(suffix + "-MANUAL", cashier, "REFUNDED", new BigDecimal("60.00"),
                new BigDecimal("60.00"), new BigDecimal("10.00"), new BigDecimal("60.00"));

        assertThat(financeRiskQuery.listCashierRiskSummaries(LocalDate.now().atStartOfDay(), LocalDateTime.now())
                .stream().filter(row -> cashier.equals(row.getCashierName())).findFirst())
                .isPresent();
        assertThat(financeRiskQuery.listAbnormalOrders(LocalDate.now().atStartOfDay(), LocalDateTime.now())
                .stream().map(row -> row.getOrderNo())).contains(suffix + "-LOSS", suffix + "-MANUAL");

        java.util.Map<String, Object> result = financeRiskService.getRiskSummary(LocalDate.now().toString(), LocalDate.now().toString());
        assertThat((Integer) result.get("abnormalOrderCount")).isGreaterThanOrEqualTo(2);
        assertThat((BigDecimal) result.get("totalLossAmount")).isGreaterThanOrEqualTo(new BigDecimal("10.00"));
        assertThat((BigDecimal) result.get("totalManualDiscount")).isGreaterThanOrEqualTo(new BigDecimal("60.00"));
        assertThat((Long) result.get("totalRefundCount")).isGreaterThanOrEqualTo(1L);

        java.util.List<?> rows = (java.util.List<?>) result.get("recentAbnormalOrders");
        assertThat(rows.stream().map(row -> String.valueOf(((java.util.Map<?, ?>) row).get("orderNo"))))
                .contains(suffix + "-LOSS", suffix + "-MANUAL");
    }

    @Test
    void shiftHandoverUsesTradeSnapshotsAndGmsBrandNames() {
        String suffix = "S" + (System.nanoTime() % 1_000_000_000L);
        String cashier = "shift-" + suffix;
        GmsBrand brand = new GmsBrand();
        brand.setName("B" + (System.nanoTime() % 1_000_000L));
        brand.setLogo("");
        brand.setDescription("finance shift test");
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);

        insertShiftOrder(suffix, cashier, new BigDecimal("20.00"), new BigDecimal("2.00"),
                new BigDecimal("1.00"), new BigDecimal("3.00"), new BigDecimal("5.00"));
        insertPayment(suffix, "CASH", null, new BigDecimal("20.00"));
        insertShiftDetail(suffix, brand.getId(), new BigDecimal("20.00"), new BigDecimal("2.00"));

        String start = LocalDateTime.now().minusMinutes(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        com.money.dto.Finance.FinanceDataVO.ShiftHandoverVO handover = financeShiftService.getShiftHandover(start, cashier);

        assertThat(handover.getCashPay()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(handover.getManualDiscount()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(handover.getVoucherDiscount()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(handover.getMemberCouponPay()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(handover.getWaivedCouponAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(handover.getBrandMatrix()).anySatisfy(row -> {
            assertThat(row.getBrandName()).isEqualTo(brand.getName());
            assertThat(row.getRevenue()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(row.getCouponConsumption()).isEqualByComparingTo(new BigDecimal("2.00"));
        });
    }

    @Test
    void profitRankingAndCampaignReviewUseSeparateTradeSnapshots() {
        String suffix = "P" + (System.nanoTime() % 1_000_000_000L);
        insertShiftOrder(suffix, "profit-" + suffix, new BigDecimal("20.00"), new BigDecimal("2.00"),
                BigDecimal.ZERO, new BigDecimal("3.00"), BigDecimal.ZERO);
        insertShiftDetail(suffix, null, new BigDecimal("20.00"), new BigDecimal("2.00"));

        assertThat(financeProfitService.getProfitRanking()).anySatisfy(row -> {
            assertThat(row.getGoodsName()).isEqualTo("Shift test goods");
            assertThat(row.getTotalSales()).isGreaterThanOrEqualTo(new BigDecimal("20.00"));
            assertThat(row.getTotalProfit()).isGreaterThanOrEqualTo(new BigDecimal("20.00"));
        });
        assertThat(financeProfitService.getCampaignReview()).anySatisfy(row -> {
            assertThat(row.getRuleName()).isEqualTo("Campaign " + suffix);
            assertThat(row.getUsedCount()).isEqualTo(1);
            assertThat(row.getTotalDiscountGived()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(row.getTotalRevenueBrought()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(row.getRoiMultiplier()).isEqualByComparingTo(new BigDecimal("6.67"));
        });
    }

    @Test
    void operatingAnalysisUsesTradePeriodSnapshotsWithClosedRangesAndFinancialStates() {
        String suffix = "OA" + (System.nanoTime() % 1_000_000_000L);
        LocalDate date = LocalDate.of(2027, 2, 1);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        insertAnalysisOrder(suffix + "-PAID", "PAID", start, new BigDecimal("20.00"), new BigDecimal("5.00"), 2);
        insertAnalysisOrder(suffix + "-PARTIAL", "PARTIAL_REFUNDED", end, new BigDecimal("10.00"), new BigDecimal("3.00"), 1);
        insertAnalysisOrder(suffix + "-REFUND", "REFUNDED", start.plusHours(1), new BigDecimal("99.00"), BigDecimal.ZERO, 9);
        insertAnalysisOrder(suffix + "-OUTSIDE", "PAID", date.plusDays(1).atStartOfDay(), new BigDecimal("88.00"), BigDecimal.ZERO, 8);

        java.util.List<FinanceOperatingMetricSnapshot> daily = financeOperatingAnalysisQuery
                .listPeriodMetrics(start, end, "DAILY");
        assertThat(daily).singleElement().satisfies(row -> {
            assertThat(row.getPeriod()).isEqualTo("2027-02-01");
            assertThat(row.getOrderCount()).isEqualTo(2L);
            assertThat(row.getGoodsCount()).isEqualTo(3L);
            assertThat(row.getNetSalesAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(row.getCostAmount()).isEqualByComparingTo(new BigDecimal("8.00"));
        });
        assertThat(financeOperatingAnalysisQuery.listPeriodMetrics(start, end, "WEEKLY"))
                .singleElement().extracting(FinanceOperatingMetricSnapshot::getOrderCount).isEqualTo(2L);
        assertThat(financeOperatingAnalysisQuery.listPeriodMetrics(start, end, "MONTHLY"))
                .singleElement().extracting(FinanceOperatingMetricSnapshot::getPeriod).isEqualTo("2027-02");

        java.util.List<PerformanceReportVO> report = salesAnalysisService
                .getPerformanceReport(date.toString(), date.toString(), "DAILY");
        assertThat(report).singleElement().satisfies(row -> {
            assertThat(row.getOrderCount()).isEqualTo(2);
            assertThat(row.getGoodsCount()).isEqualTo(3);
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(row.getAvgOrderValue()).isEqualByComparingTo(new BigDecimal("15.00"));
        });
        OrderCountVO totals = salesAnalysisService.countOrderAndSales(start, end);
        assertThat(totals.getOrderCount()).isEqualTo(2L);
        assertThat(totals.getTotalSales()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(totals.getCostCount()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(totals.getProfit()).isEqualByComparingTo(new BigDecimal("22.00"));
    }

    @Test
    void salesDashboardUsesTradeSnapshotsAndGmsBrandNames() {
        String suffix = "D" + (System.nanoTime() % 1_000_000_000L);
        GmsBrand brand = new GmsBrand();
        brand.setName("D" + (System.nanoTime() % 1_000_000L));
        brand.setLogo("");
        brand.setDescription("dashboard snapshot test");
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);

        LocalDateTime start = LocalDateTime.now().minusMinutes(1).withNano(0);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1).withNano(0);
        insertDashboardOrder(suffix + "-MEMBER", "PAID", start, new BigDecimal("20.00"),
                new BigDecimal("2.00"), 2, 0, brand.getId(), true, "Dashboard member " + suffix);
        insertDashboardOrder(suffix + "-GUEST", "PARTIAL_REFUNDED", end, new BigDecimal("10.00"),
                new BigDecimal("1.00"), 1, 0, null, false, "Dashboard guest " + suffix);
        insertDashboardOrder(suffix + "-ZERO", "PAID", start.plusSeconds(1), new BigDecimal("8.00"),
                BigDecimal.ZERO, 1, 1, brand.getId(), false, "Dashboard zero " + suffix);
        insertDashboardOrder(suffix + "-REFUND", "REFUNDED", start.plusSeconds(2), new BigDecimal("99.00"),
                BigDecimal.ZERO, 9, 0, brand.getId(), true, "Dashboard refunded " + suffix);

        assertThat(financeSalesDashboardQuery.listTopGoods(start, end)).anySatisfy(row -> {
            assertThat(row.getGoodsName()).isEqualTo("Dashboard member " + suffix);
            assertThat(row.getSalesQuantity()).isEqualTo(2L);
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        });
        assertThat(financeSalesDashboardQuery.listTopGoods(start, end).stream().map(row -> row.getGoodsName()))
                .doesNotContain("Dashboard zero " + suffix, "Dashboard refunded " + suffix);
        assertThat(financeSalesDashboardQuery.listBrandSales(start, end)).anySatisfy(row -> {
            assertThat(row.getBrandId()).isEqualTo(brand.getId());
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        });
        assertThat(financeSalesDashboardQuery.listDailyMemberMetrics(start, end)).anySatisfy(row -> {
            assertThat(row.isMember()).isTrue();
            assertThat(row.getOrderCount()).isEqualTo(1L);
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        });

        String startText = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endText = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        SalesDashboardVO dashboard = salesAnalysisService.getSalesDashboard(startText, endText);
        assertThat(dashboard.getTotalSalesAmount()).isEqualByComparingTo(new BigDecimal("38.00"));
        assertThat(dashboard.getTotalOrderCount()).isEqualTo(3);
        assertThat(dashboard.getTotalGoodsCount()).isEqualTo(3);
        assertThat(dashboard.getTopGoodsRanking().stream().map(row -> row.getGoodsName()))
                .contains("Dashboard member " + suffix, "Dashboard guest " + suffix)
                .doesNotContain("Dashboard zero " + suffix, "Dashboard refunded " + suffix);
        assertThat(dashboard.getBrandDistribution()).anySatisfy(row -> {
            assertThat(row.getBrandName()).isEqualTo(brand.getName());
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        });
        assertThat(dashboard.getBrandDistribution()).anySatisfy(row -> {
            assertThat(row.getBrandName()).isEqualTo("无品牌/未知");
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        });
        assertThat(dashboard.getMemberTrend().getMemberSales()).contains(new BigDecimal("20.00"));
        assertThat(dashboard.getMemberTrend().getGuestSales()).contains(new BigDecimal("18.00"));
    }

    @Test
    void productAnalysisUsesTradeSnapshotsAndGmsCategoryNames() {
        String suffix = "CA" + (System.nanoTime() % 1_000_000L);
        GmsGoodsCategory category = new GmsGoodsCategory();
        category.setPid(0L);
        category.setIcon("");
        category.setName(suffix);
        category.setGoodsCount(0);
        category.setTenantId(0L);
        goodsCategoryMapper.insert(category);

        LocalDate date = LocalDate.of(2027, 3, 1);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atTime(LocalTime.MAX);
        insertProductAnalysisOrder(suffix + "-KNOWN", "PAID", start, 887L, category.getId(),
                "Hist " + suffix, new BigDecimal("10.00"), 3, 1);
        insertProductAnalysisOrder(suffix + "-UNKNOWN", "PARTIAL_REFUNDED", date.plusDays(1).atStartOfDay(),
                888L, null, "Other " + suffix, new BigDecimal("5.00"), 2, 1);
        insertProductAnalysisOrder(suffix + "-ZERO", "PAID", start.plusHours(1), 887L, category.getId(),
                "Hist " + suffix, new BigDecimal("10.00"), 1, 1);
        insertProductAnalysisOrder(suffix + "-REFUNDED", "REFUNDED", start.plusHours(2), 889L, category.getId(),
                "Refunded " + suffix, new BigDecimal("99.00"), 5, 0);

        assertThat(financeProductAnalysisQuery.listCategorySales(start, end)).anySatisfy(row -> {
            assertThat(row.getCategoryId()).isEqualTo(category.getId());
            assertThat(row.getSalesQuantity()).isEqualTo(2L);
            assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        });
        assertThat(financeProductAnalysisQuery.listDailyGoodsMetrics(start, end,
                java.util.Arrays.asList(887L, 888L))).anySatisfy(row -> {
            assertThat(row.getGoodsId()).isEqualTo(887L);
            assertThat(row.getDate()).isEqualTo(date.toString());
            assertThat(row.getSalesQuantity()).isEqualTo(2L);
        });
        assertThat(salesAnalysisService.getCategorySales(date.toString(), date.plusDays(1).toString()))
                .anySatisfy(row -> {
                    assertThat(row.getCategoryName()).isEqualTo(category.getName());
                    assertThat(row.getSalesQty()).isEqualTo(2);
                    assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
                })
                .anySatisfy(row -> {
                    assertThat(row.getCategoryName()).isEqualTo("未分类");
                    assertThat(row.getSalesQty()).isEqualTo(1);
                    assertThat(row.getSalesAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
                });
        assertThat(salesAnalysisService.getTopGoodsTrend(date.toString(), date.plusDays(1).toString(),
                java.util.Arrays.asList(887L, 999999L))).anySatisfy(row -> {
            assertThat(row.getGoodsId()).isEqualTo(887L);
            assertThat(row.getGoodsName()).isEqualTo("Hist " + suffix);
            assertThat(row.getTrendSalesQty()).containsExactly(2, 0);
        }).anySatisfy(row -> {
            assertThat(row.getGoodsId()).isEqualTo(999999L);
            assertThat(row.getGoodsName()).isEqualTo("商品 ID:999999");
            assertThat(row.getTrendSalesQty()).containsExactly(0, 0);
        });
    }

    @Test
    void trafficAnalysisUsesTradeMetricsAndSystemStrategySnapshots() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(1).withNano(0);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1).withNano(0);
        int hour = LocalDateTime.now().getHour();
        long beforeCount = financeTrafficQuery.listHourlyMetrics(start, end, null, 1.0).stream()
                .filter(row -> row.getHour() == hour).mapToLong(row -> row.getTotalOrderCount()).sum();

        String suffix = "T" + (System.nanoTime() % 1_000_000_000L);
        insertOrder(suffix + "-PAID", "PAID", new BigDecimal("12.00"), new BigDecimal("12.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        insertOrder(suffix + "-PARTIAL", "PARTIAL_REFUNDED", new BigDecimal("8.00"), new BigDecimal("8.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        insertOrder(suffix + "-REFUND", "REFUNDED", new BigDecimal("99.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(financeTrafficQuery.listHourlyMetrics(start, end, null, 1.0)).anySatisfy(row -> {
            assertThat(row.getHour()).isEqualTo(hour);
            assertThat(row.getTotalOrderCount()).isEqualTo(beforeCount + 2L);
            assertThat(row.getTotalSalesAmount()).isGreaterThanOrEqualTo(new BigDecimal("20.00"));
        });

        SysStrategy strategy = strategyMapper.getGlobalStrategy();
        if (strategy == null) {
            strategy = new SysStrategy();
            strategy.setTenantId(0L);
            strategyMapper.insert(strategy);
        }
        strategy.setTrafficOrderThreshold(new BigDecimal("999"));
        strategy.setTrafficValueThreshold(new BigDecimal("999"));
        strategy.setWeeklyAnalysisDays(7);
        strategy.setMonthlyAnalysisDays(30);
        strategyMapper.updateById(strategy);

        assertThat(financeTrafficStrategyQuery.getTrafficStrategy().getWeeklyAnalysisDays()).isEqualTo(7);
        assertThat(salesAnalysisService.getTrafficAnalysis(null)).anySatisfy(row -> {
            assertThat(row.getHour()).isEqualTo(hour);
            assertThat(row.getSuggestion()).isEqualTo("OUT");
            assertThat(row.getSampleDays()).isEqualTo(28);
        });
        assertThat(salesAnalysisService.getWeeklyTraffic()).anySatisfy(row -> {
            assertThat(row.getSampleDays()).isEqualTo(1.0);
            assertThat(row.getTotalOrderCount()).isGreaterThanOrEqualTo(new BigDecimal("2"));
        });
        assertThat(salesAnalysisService.getMonthlyTraffic()).anySatisfy(row -> {
            assertThat(row.getSampleDays()).isEqualTo(30 / 30.43);
            assertThat(row.getTotalSalesAmount()).isGreaterThanOrEqualTo(new BigDecimal("20.00"));
        });
    }

    private void insertInventoryDocument(String docNo, String docType, java.math.BigDecimal totalAmount) {
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo(docNo);
        doc.setDocType(docType);
        doc.setTotalQty(1);
        doc.setTotalAmount(totalAmount);
        doc.setOperator("finance-p2-test");
        doc.setTenantId(0L);
        doc.setCreateTime(LocalDateTime.now());
        inventoryDocMapper.insert(doc);
    }

    private void insertMemberLog(Long memberId, String operateType, BigDecimal realAmount) {
        UmsMemberLog log = new UmsMemberLog();
        log.setMemberId(memberId);
        log.setType("BALANCE");
        log.setOperateType(operateType);
        log.setAmount(realAmount);
        log.setAfterAmount(realAmount);
        log.setRealAmount(realAmount);
        log.setTenantId("0");
        log.setCreateTime(LocalDateTime.now());
        memberLogMapper.insert(log);
    }

    private void insertOrder(String orderNo, String status, BigDecimal totalAmount, BigDecimal finalSalesAmount,
                             BigDecimal actualCouponDeduct, BigDecimal waivedCouponAmount,
                             BigDecimal useVoucherAmount, BigDecimal manualDiscountAmount) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setVip(false);
        order.setTotalAmount(totalAmount);
        order.setCouponAmount(actualCouponDeduct);
        order.setActualCouponDeduct(actualCouponDeduct);
        order.setWaivedCouponAmount(waivedCouponAmount);
        order.setUseVoucherAmount(useVoucherAmount);
        order.setManualDiscountAmount(manualDiscountAmount);
        order.setPayAmount(totalAmount.subtract(actualCouponDeduct).subtract(waivedCouponAmount)
                .subtract(useVoucherAmount).subtract(manualDiscountAmount));
        order.setFinalSalesAmount(finalSalesAmount);
        order.setCostAmount(BigDecimal.ZERO);
        order.setPaymentTime(LocalDateTime.now());
        order.setTenantId(0L);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    private void insertPayment(String orderNo, String methodCode, String payTag, BigDecimal netAmount) {
        OmsOrderPay payment = new OmsOrderPay();
        payment.setOrderNo(orderNo);
        payment.setPayMethodCode(methodCode);
        payment.setPayMethodName(methodCode);
        payment.setPayTag(payTag);
        payment.setPayAmount(netAmount);
        payment.setOriginalAmount(netAmount);
        payment.setNetAmount(netAmount);
        payment.setCreateTime(LocalDateTime.now());
        orderPayMapper.insert(payment);
    }

    private void insertRiskOrder(String orderNo, String cashier, String status, BigDecimal payAmount,
                                 BigDecimal finalSalesAmount, BigDecimal costAmount, BigDecimal manualDiscountAmount) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setVip(false);
        order.setTotalAmount(payAmount);
        order.setPayAmount(payAmount);
        order.setFinalSalesAmount(finalSalesAmount);
        order.setCostAmount(costAmount);
        order.setManualDiscountAmount(manualDiscountAmount);
        order.setActualCouponDeduct(BigDecimal.ZERO);
        order.setWaivedCouponAmount(BigDecimal.ZERO);
        order.setUseVoucherAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setPaymentTime(LocalDateTime.now());
        order.setTenantId(0L);
        order.setCreateBy(cashier);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    private void insertShiftOrder(String orderNo, String cashier, BigDecimal payAmount, BigDecimal actualCouponDeduct,
                                  BigDecimal waivedCouponAmount, BigDecimal voucherAmount, BigDecimal manualDiscount) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setStatus("PAID");
        order.setVip(false);
        order.setTotalAmount(payAmount);
        order.setPayAmount(payAmount);
        order.setFinalSalesAmount(payAmount);
        order.setCostAmount(BigDecimal.ZERO);
        order.setActualCouponDeduct(actualCouponDeduct);
        order.setCouponAmount(actualCouponDeduct);
        order.setWaivedCouponAmount(waivedCouponAmount);
        order.setUseVoucherAmount(voucherAmount);
        order.setManualDiscountAmount(manualDiscount);
        order.setRemark("Campaign " + orderNo);
        order.setPaymentTime(LocalDateTime.now());
        order.setTenantId(0L);
        order.setCreateBy(cashier);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    private void insertShiftDetail(String orderNo, Long brandId, BigDecimal goodsPrice, BigDecimal coupon) {
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderNo(orderNo);
        detail.setStatus("PAID");
        detail.setGoodsId(1L);
        detail.setBrandId(brandId);
        detail.setGoodsBarcode("SHIFT-TEST");
        detail.setGoodsName("Shift test goods");
        detail.setGoodsPrice(goodsPrice);
        detail.setSalePrice(goodsPrice);
        detail.setPurchasePrice(BigDecimal.ZERO);
        detail.setVipPrice(goodsPrice);
        detail.setQuantity(1);
        detail.setReturnQuantity(0);
        detail.setCoupon(coupon);
        detail.setTenantId(0L);
        orderDetailMapper.insert(detail);
    }

    private void insertAnalysisOrder(String orderNo, String status, LocalDateTime createTime,
                                     BigDecimal salesAmount, BigDecimal costAmount, int quantity) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setVip(false);
        order.setTotalAmount(salesAmount);
        order.setPayAmount(salesAmount);
        order.setFinalSalesAmount(salesAmount);
        order.setCostAmount(costAmount);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setActualCouponDeduct(BigDecimal.ZERO);
        order.setWaivedCouponAmount(BigDecimal.ZERO);
        order.setUseVoucherAmount(BigDecimal.ZERO);
        order.setManualDiscountAmount(BigDecimal.ZERO);
        order.setTenantId(0L);
        order.setPaymentTime(createTime);
        order.setCreateTime(createTime);
        orderMapper.insert(order);

        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderNo(orderNo);
        detail.setStatus(status);
        detail.setGoodsId(9L);
        detail.setGoodsBarcode("OPERATING-TEST");
        detail.setGoodsName("Operating test goods");
        detail.setGoodsPrice(salesAmount);
        detail.setSalePrice(salesAmount);
        detail.setPurchasePrice(costAmount);
        detail.setVipPrice(salesAmount);
        detail.setQuantity(quantity);
        detail.setReturnQuantity(0);
        detail.setCoupon(BigDecimal.ZERO);
        detail.setTenantId(0L);
        orderDetailMapper.insert(detail);
    }

    private void insertDashboardOrder(String orderNo, String status, LocalDateTime createTime,
                                      BigDecimal salesAmount, BigDecimal costAmount, int quantity, int returnQuantity,
                                      Long brandId, boolean member, String goodsName) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setVip(member);
        order.setTotalAmount(salesAmount);
        order.setPayAmount(salesAmount);
        order.setFinalSalesAmount(salesAmount);
        order.setCostAmount(costAmount);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setActualCouponDeduct(BigDecimal.ZERO);
        order.setWaivedCouponAmount(BigDecimal.ZERO);
        order.setUseVoucherAmount(BigDecimal.ZERO);
        order.setManualDiscountAmount(BigDecimal.ZERO);
        order.setTenantId(0L);
        order.setPaymentTime(createTime);
        order.setCreateTime(createTime);
        orderMapper.insert(order);

        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderNo(orderNo);
        detail.setStatus(status);
        detail.setGoodsId(77L);
        detail.setBrandId(brandId);
        detail.setGoodsBarcode("DASHBOARD-TEST");
        detail.setGoodsName(goodsName);
        detail.setGoodsPrice(salesAmount);
        detail.setSalePrice(salesAmount);
        detail.setPurchasePrice(costAmount);
        detail.setVipPrice(salesAmount);
        detail.setQuantity(quantity);
        detail.setReturnQuantity(returnQuantity);
        detail.setCoupon(BigDecimal.ZERO);
        detail.setTenantId(0L);
        orderDetailMapper.insert(detail);
    }

    private void insertProductAnalysisOrder(String orderNo, String status, LocalDateTime createTime,
                                            Long goodsId, Long categoryId, String goodsName,
                                            BigDecimal goodsPrice, int quantity, int returnQuantity) {
        OmsOrder order = new OmsOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setVip(false);
        order.setTotalAmount(goodsPrice.multiply(BigDecimal.valueOf(quantity)));
        order.setPayAmount(order.getTotalAmount());
        order.setFinalSalesAmount(order.getTotalAmount());
        order.setCostAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setActualCouponDeduct(BigDecimal.ZERO);
        order.setWaivedCouponAmount(BigDecimal.ZERO);
        order.setUseVoucherAmount(BigDecimal.ZERO);
        order.setManualDiscountAmount(BigDecimal.ZERO);
        order.setTenantId(0L);
        order.setPaymentTime(createTime);
        order.setCreateTime(createTime);
        orderMapper.insert(order);

        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderNo(orderNo);
        detail.setStatus(status);
        detail.setGoodsId(goodsId);
        detail.setCategoryId(categoryId);
        detail.setGoodsBarcode("PRODUCT-ANALYSIS-TEST");
        detail.setGoodsName(goodsName);
        detail.setGoodsPrice(goodsPrice);
        detail.setSalePrice(goodsPrice);
        detail.setPurchasePrice(BigDecimal.ZERO);
        detail.setVipPrice(goodsPrice);
        detail.setQuantity(quantity);
        detail.setReturnQuantity(returnQuantity);
        detail.setCoupon(BigDecimal.ZERO);
        detail.setTenantId(0L);
        orderDetailMapper.insert(detail);
    }
}
