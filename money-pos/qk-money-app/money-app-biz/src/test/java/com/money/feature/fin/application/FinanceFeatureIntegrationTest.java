package com.money.feature.fin.application;

import com.money.feature.fin.application.dashboard.FinanceDashboardService;
import com.money.feature.fin.application.report.FinanceReportService;
import com.money.feature.fin.application.report.FinanceShiftService;
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
}
