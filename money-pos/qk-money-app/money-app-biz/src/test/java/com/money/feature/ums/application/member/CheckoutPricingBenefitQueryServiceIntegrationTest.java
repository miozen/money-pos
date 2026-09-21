package com.money.feature.ums.application.member;

import com.money.contract.member.CheckoutPricingBenefitQuery;
import com.money.contract.member.CheckoutPricingBenefitSnapshot;
import com.money.entity.PosCouponRule;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.support.TradeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class CheckoutPricingBenefitQueryServiceIntegrationTest {

    @Autowired
    private CheckoutPricingBenefitQuery checkoutPricingBenefitQuery;
    @Autowired
    private UmsMemberBrandLevelMapper memberBrandLevelMapper;
    @Autowired
    private TradeFixture tradeFixture;

    @BeforeEach
    void authenticateFixtureWriter() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsOnlyPricingBenefitsWithoutExposingPersistenceEntities() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));
        UmsMemberBrandLevel level = new UmsMemberBrandLevel();
        level.setMemberId(member.getId());
        level.setBrand(" 100 ");
        level.setLevelCode("VIP");
        level.setTenantId(0L);
        memberBrandLevelMapper.insert(level);

        CheckoutPricingBenefitSnapshot benefits = checkoutPricingBenefitQuery.findForPricing(member.getId(), rule.getId());

        assertThat(benefits.getMemberBrandLevels()).containsEntry("100", "VIP");
        assertThat(benefits.getVoucherRule()).isNotNull();
        assertThat(benefits.getVoucherRule().getThresholdAmount()).isEqualByComparingTo("20.00");
        assertThat(benefits.getVoucherRule().getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(checkoutPricingBenefitQuery.findForPricing(null, null).getMemberBrandLevels()).isEmpty();
        assertThat(checkoutPricingBenefitQuery.findForPricing(null, null).getVoucherRule()).isNull();
    }
}
