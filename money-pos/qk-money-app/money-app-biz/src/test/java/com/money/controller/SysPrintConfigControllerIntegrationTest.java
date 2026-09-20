package com.money.controller;

import com.money.feature.sys.infrastructure.persistence.entity.SysPrintConfig;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysPrintConfigControllerIntegrationTest {

    @Autowired
    private SysPrintConfigController printConfigController;

    @BeforeEach
    void bindRequestContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/config/print");
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void configurationRouteKeepsTheFixedIdAndAllPrintSettings() {
        SysPrintConfig update = new SysPrintConfig();
        update.setId(999L);
        update.setShopName("AD-2.29 接口门店");
        update.setShopPhone("13700000000");
        update.setShopAddress("接口测试地址");
        update.setHeaderMsg("接口欢迎语");
        update.setFooterMsg("接口页脚");
        update.setAutoPrint(false);
        update.setOpenDrawer(true);

        assertThat(printConfigController.updateConfig(update)).isTrue();
        assertThat(update.getId()).isEqualTo(1L);
        assertThat(printConfigController.getConfig())
                .extracting(SysPrintConfig::getId,
                        SysPrintConfig::getShopName,
                        SysPrintConfig::getShopPhone,
                        SysPrintConfig::getShopAddress,
                        SysPrintConfig::getHeaderMsg,
                        SysPrintConfig::getFooterMsg,
                        SysPrintConfig::getAutoPrint,
                        SysPrintConfig::getOpenDrawer)
                .containsExactly(1L, "AD-2.29 接口门店", "13700000000", "接口测试地址", "接口欢迎语", "接口页脚", false, true);
    }
}
