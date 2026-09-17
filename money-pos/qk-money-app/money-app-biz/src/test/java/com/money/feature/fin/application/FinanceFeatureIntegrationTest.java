package com.money.feature.fin.application;

import com.money.feature.fin.application.dashboard.FinanceDashboardService;
import com.money.feature.fin.application.report.FinanceReportService;
import com.money.feature.fin.application.report.FinanceShiftService;
import com.money.contract.member.FinanceMemberAssetCompositionSnapshot;
import com.money.contract.member.FinanceMemberAssetQuery;
import com.money.dto.Finance.FinanceDataVO.FinanceDashboardVO;
import com.money.entity.GmsInventoryDoc;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.mapper.GmsInventoryDocMapper;
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
    private GmsInventoryDocMapper inventoryDocMapper;
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
}
