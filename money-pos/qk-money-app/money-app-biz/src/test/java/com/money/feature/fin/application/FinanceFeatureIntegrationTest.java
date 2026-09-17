package com.money.feature.fin.application;

import com.money.feature.fin.application.dashboard.FinanceDashboardService;
import com.money.feature.fin.application.report.FinanceReportService;
import com.money.feature.fin.application.report.FinanceShiftService;
import com.money.feature.fin.application.analysis.FinanceRiskService;
import com.money.contract.member.FinanceMemberAssetCompositionSnapshot;
import com.money.contract.member.FinanceMemberAssetQuery;
import com.money.contract.trade.FinanceRiskQuery;
import com.money.dto.Finance.FinanceDataVO.FinanceDashboardVO;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.GmsBrand;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.OmsOrderPay;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
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
    private FinanceRiskQuery financeRiskQuery;

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
    private FinanceMemberAssetQuery financeMemberAssetQuery;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberLogMapper memberLogMapper;
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
}
