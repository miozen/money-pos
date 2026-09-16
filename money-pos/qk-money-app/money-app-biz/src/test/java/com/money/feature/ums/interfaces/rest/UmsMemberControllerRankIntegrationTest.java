package com.money.feature.ums.interfaces.rest;

import com.money.dto.UmsMember.MemberRankVO;
import com.money.entity.UmsMember;
import com.money.feature.ums.application.member.UmsMemberService;
import com.money.mapper.UmsMemberMapper;
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
class UmsMemberControllerRankIntegrationTest {

    @Autowired
    private UmsMemberService umsMemberService;

    @Autowired
    private TradeFixture tradeFixture;

    @Autowired
    private UmsMemberMapper umsMemberMapper;

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
    void rankQueriesReturnTheExistingMemberRankFields() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("888888.00"));
        member.setConsumeAmount(new BigDecimal("999999.00"));
        member.setConsumeTimes(77777);
        umsMemberMapper.updateById(member);

        assertRankContains(umsMemberService.getTopConsumeMembers(), member.getId(), new BigDecimal("999999.00"), null);
        assertRankContains(umsMemberService.getTopBalanceMembers(), member.getId(), new BigDecimal("888888.00"), null);
        assertRankContains(umsMemberService.getTopFrequencyMembers(), member.getId(), null, 77777);
    }

    private void assertRankContains(List<MemberRankVO> ranks, Long memberId, BigDecimal amount, Integer times) {
        assertThat(ranks).filteredOn(rank -> memberId.equals(rank.getId())).singleElement().satisfies(rank -> {
            if (amount != null) {
                assertThat(rank.getAmount()).isEqualByComparingTo(amount);
            }
            if (times != null) {
                assertThat(rank.getTimes()).isEqualTo(times);
            }
        });
    }
}
