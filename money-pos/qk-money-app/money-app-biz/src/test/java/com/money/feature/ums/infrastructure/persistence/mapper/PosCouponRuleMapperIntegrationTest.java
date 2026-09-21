package com.money.feature.ums.infrastructure.persistence.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.mapper.PosCouponRuleMapper;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class PosCouponRuleMapperIntegrationTest {

    @Autowired
    private PosCouponRuleMapper posCouponRuleMapper;

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
    void mapperCrudUsesTheUmsLocalEntityAndExplicitCouponRuleTable() {
        String suffix = Long.toString(System.nanoTime(), 36);
        PosCouponRule rule = new PosCouponRule();
        rule.setName("AD253-" + suffix);
        rule.setThresholdAmount(new BigDecimal("20.00"));
        rule.setDiscountAmount(new BigDecimal("5.00"));
        rule.setStatus(1);
        rule.setCreateBy("mapper-test");
        rule.setCreateTime(LocalDateTime.of(2025, 1, 2, 3, 4, 5));
        rule.setUpdateTime(LocalDateTime.of(2025, 1, 2, 3, 4, 6));
        rule.setTenantId("0");

        assertThat(posCouponRuleMapper.insert(rule)).isEqualTo(1);
        assertThat(rule.getId()).isNotNull();
        assertThat(posCouponRuleMapper.selectById(rule.getId()))
                .extracting(PosCouponRule::getName, PosCouponRule::getThresholdAmount,
                        PosCouponRule::getDiscountAmount, PosCouponRule::getStatus,
                        PosCouponRule::getCreateBy, PosCouponRule::getCreateTime,
                        PosCouponRule::getUpdateTime, PosCouponRule::getTenantId)
                .containsExactly(rule.getName(), new BigDecimal("20.00"), new BigDecimal("5.00"), 1,
                        "mapper-test", rule.getCreateTime(), rule.getUpdateTime(), "0");

        rule.setStatus(0);
        rule.setDiscountAmount(new BigDecimal("6.00"));
        assertThat(posCouponRuleMapper.updateById(rule)).isEqualTo(1);
        assertThat(posCouponRuleMapper.selectById(rule.getId()))
                .extracting(PosCouponRule::getStatus, PosCouponRule::getDiscountAmount)
                .containsExactly(0, new BigDecimal("6.00"));

        assertThat(posCouponRuleMapper.deleteById(rule.getId())).isEqualTo(1);
        assertThat(posCouponRuleMapper.selectById(rule.getId())).isNull();
    }
}
