package com.money.service;

import com.money.entity.SysStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysStrategyServiceIntegrationTest {

    @Autowired
    private SysStrategyService sysStrategyService;

    @Test
    void savesTheExistingGlobalStrategyWithoutChangingItsIdentity() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            SysStrategy existing = sysStrategyService.getGlobalStrategy();
            assertThat(existing.getId()).isNotNull();

            existing.setTurnoverTargetDays(21);
            sysStrategyService.saveGlobalStrategy(existing);

            SysStrategy saved = sysStrategyService.getGlobalStrategy();
            assertThat(saved.getId()).isEqualTo(existing.getId());
            assertThat(saved.getTurnoverTargetDays()).isEqualTo(21);
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
