package com.money.controller;

import com.money.entity.SysUser;
import com.money.service.SysUserService;
import com.money.vo.LoginCandidateVO;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysAuthControllerIntegrationTest {

    @Autowired
    private SysAuthController sysAuthController;

    @Autowired
    private SysUserService sysUserService;

    @BeforeEach
    void bindRequestContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = loopbackRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void localCandidatesExposeOnlyEnabledAccountsAndNoStoreResponse() {
        String suffix = UUID.randomUUID().toString();
        SysUser enabled = new SysUser();
        enabled.setUsername("ux11-enabled-" + suffix);
        enabled.setNickname("UX11 可用账号");
        enabled.setPassword("not-used-by-candidate-query");
        enabled.setEnabled(true);
        sysUserService.save(enabled);

        SysUser disabled = new SysUser();
        disabled.setUsername("ux11-disabled-" + suffix);
        disabled.setNickname("UX11 停用账号");
        disabled.setPassword("not-used-by-candidate-query");
        disabled.setEnabled(false);
        sysUserService.save(disabled);

        MockHttpServletResponse response = new MockHttpServletResponse();
        List<LoginCandidateVO> candidates = sysAuthController.loginCandidates(loopbackRequest(), response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.getUsername()).isEqualTo("ux11-enabled-" + suffix);
            assertThat(candidate.getDisplayName()).isEqualTo("UX11 可用账号");
        });
        assertThat(candidates)
                .extracting(LoginCandidateVO::getUsername)
                .doesNotContain("ux11-disabled-" + suffix);
    }

    @Test
    void candidateEndpointRejectsTenantOverrideAndRemoteCallers() {
        MockHttpServletRequest tenantOverride = loopbackRequest();
        tenantOverride.addHeader("Y-tenant", "1");
        assertThatThrownBy(() -> sysAuthController.loginCandidates(tenantOverride, new MockHttpServletResponse()))
                .isInstanceOf(BaseException.class);

        MockHttpServletRequest remoteRequest = loopbackRequest();
        remoteRequest.setRemoteAddr("192.0.2.10");
        assertThatThrownBy(() -> sysAuthController.loginCandidates(remoteRequest, new MockHttpServletResponse()))
                .isInstanceOf(BaseException.class);
    }

    private MockHttpServletRequest loopbackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login-candidates");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
