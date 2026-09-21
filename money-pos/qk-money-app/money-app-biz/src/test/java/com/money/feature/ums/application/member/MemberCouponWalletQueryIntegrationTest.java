package com.money.feature.ums.application.member;

import com.money.contract.member.MemberCouponCountQuery;
import com.money.contract.member.MemberCouponWalletQuery;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
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
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberCouponWalletQueryIntegrationTest {

    @Autowired private MemberCouponCountQuery memberCouponCountQuery;
    @Autowired private MemberCouponWalletQuery memberCouponWalletQuery;
    @Autowired private TradeFixture tradeFixture;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
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
    void returnsUnusedCountsWithoutExposingMemberCouponEntity() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule firstRule = tradeFixture.createCouponRule("first-" + suffix, new BigDecimal("20"), new BigDecimal("5"));
        PosCouponRule secondRule = tradeFixture.createCouponRule("second-" + suffix, new BigDecimal("30"), new BigDecimal("6"));
        tradeFixture.issueCoupon(member.getId(), firstRule.getId());
        tradeFixture.issueCoupon(member.getId(), firstRule.getId());
        tradeFixture.issueCoupon(member.getId(), secondRule.getId());

        assertThat(memberCouponWalletQuery.countUnusedCouponsByRuleId(member.getId()))
                .containsEntry(firstRule.getId(), 2L).containsEntry(secondRule.getId(), 1L);
        Map<Long, Long> counts = memberCouponCountQuery.countUnusedCouponsByMemberIds(Arrays.asList(member.getId(), 999999L));
        assertThat(counts).containsEntry(member.getId(), 3L).doesNotContainKey(999999L);
        assertThat(memberCouponWalletQuery.countUnusedCouponsByRuleId(null)).isEmpty();
    }
}
