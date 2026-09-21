package com.money.controller;

import com.money.contract.member.CouponRuleManagementCommand;
import com.money.contract.member.CouponRuleManagementSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.support.TradeFixture;
import com.money.web.vo.PageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PosCouponRuleControllerIntegrationTest {

    @Autowired
    private PosCouponRuleController posCouponRuleController;

    @Autowired
    private TradeFixture tradeFixture;
    @Autowired
    private ObjectMapper objectMapper;

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
    void managesRulesAndReturnsUnusedMemberCouponCounts() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        CouponRuleManagementCommand rule = new CouponRuleManagementCommand();
        rule.setName("managed-" + suffix);
        rule.setThresholdAmount(new BigDecimal("30.00"));
        rule.setDiscountAmount(new BigDecimal("8.00"));
        rule.setStatus(1);
        rule.setTenantId("0");
        posCouponRuleController.add(rule);

        PageVO<CouponRuleManagementSnapshot> page = posCouponRuleController.list(1, 10, rule.getName());
        assertThat(page.getRecords()).extracting(CouponRuleManagementSnapshot::getId).contains(rule.getId());
        assertThat(objectMapper.writeValueAsString(page.getRecords().get(0)))
                .contains("\"id\"", "\"name\"", "\"thresholdAmount\"", "\"discountAmount\"",
                        "\"status\"", "\"createBy\"", "\"createTime\"", "\"updateTime\"", "\"tenantId\"");

        rule.setStatus(0);
        posCouponRuleController.update(rule);
        assertThat(posCouponRuleController.list(1, 10, rule.getName()).getRecords())
                .anySatisfy(saved -> assertThat(saved.getStatus()).isZero());

        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        tradeFixture.issueCoupon(member.getId(), rule.getId());
        tradeFixture.issueCoupon(member.getId(), rule.getId());
        List<com.money.contract.member.MemberCouponRuleSnapshot> memberCoupons = posCouponRuleController.getMemberCoupons(member.getId());
        assertThat(memberCoupons).anySatisfy(coupon -> {
            assertThat(coupon.getRuleId()).isEqualTo(rule.getId());
            assertThat(coupon.getOwnedCount()).isEqualTo(2L);
            assertThat(coupon.getThresholdAmount()).isEqualByComparingTo("30.00");
        });
        assertThat(objectMapper.writeValueAsString(memberCoupons))
                .contains("\"ruleId\"", "\"name\"", "\"thresholdAmount\"", "\"discountAmount\"", "\"ownedCount\"");

        posCouponRuleController.delete(List.of(rule.getId()));
        assertThat(posCouponRuleController.list(1, 10, rule.getName()).getRecords()).isEmpty();
    }
}
