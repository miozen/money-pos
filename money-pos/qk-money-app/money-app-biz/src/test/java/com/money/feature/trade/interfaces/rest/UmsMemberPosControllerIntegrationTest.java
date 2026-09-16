package com.money.feature.trade.interfaces.rest;

import com.money.feature.trade.application.pos.dto.CouponRuleSummary;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberPosControllerIntegrationTest {

    @Autowired
    private UmsMemberPosController umsMemberPosController;

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
    void couponRuleRouteReturnsTheStableSummaryContract() {
        String suffix = Long.toString(System.nanoTime(), 36);
        tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));

        List<CouponRuleSummary> rules = umsMemberPosController.getCouponRules();

        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.getName()).isEqualTo("C" + suffix);
            assertThat(rule.getThresholdAmount()).isEqualByComparingTo("20.00");
            assertThat(rule.getDiscountAmount()).isEqualByComparingTo("5.00");
            assertThat(rule.getStatus()).isEqualTo(1);
        });
    }
}
