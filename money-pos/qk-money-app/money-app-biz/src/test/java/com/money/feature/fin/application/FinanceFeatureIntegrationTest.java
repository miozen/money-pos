package com.money.feature.fin.application;

import com.money.feature.fin.application.dashboard.FinanceDashboardService;
import com.money.feature.fin.application.report.FinanceReportService;
import com.money.feature.fin.application.report.FinanceShiftService;
import com.money.dto.Finance.FinanceDataVO.FinanceDashboardVO;
import com.money.entity.GmsInventoryDoc;
import com.money.mapper.GmsInventoryDocMapper;
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
}
