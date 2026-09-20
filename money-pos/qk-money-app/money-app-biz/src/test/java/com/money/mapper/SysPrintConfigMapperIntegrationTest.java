package com.money.mapper;

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
class SysPrintConfigMapperIntegrationTest {

    @Autowired
    private SysPrintConfigMapper printConfigMapper;

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
    void mapperUsesTheSysLocalEntityForTheFixedSeedConfiguration() {
        SysPrintConfig config = printConfigMapper.selectById(1L);

        assertThat(config).isNotNull();
        assertThat(config).extracting(SysPrintConfig::getId,
                        SysPrintConfig::getShopName,
                        SysPrintConfig::getShopPhone,
                        SysPrintConfig::getShopAddress,
                        SysPrintConfig::getHeaderMsg,
                        SysPrintConfig::getFooterMsg,
                        SysPrintConfig::getAutoPrint,
                        SysPrintConfig::getOpenDrawer,
                        SysPrintConfig::getCreateBy,
                        SysPrintConfig::getCreateTime,
                        SysPrintConfig::getUpdateBy,
                        SysPrintConfig::getUpdateTime)
                .containsExactly(1L, "绿叶超市蒙山店", "18070641641", "特色街绿叶超市（长寿阁往南50米）", "",
                        "谢谢惠顾，欢迎再次光临！", true, true, "System", config.getCreateTime(), "money", config.getUpdateTime());

        config.setShopName("AD-2.29 配置回归");
        config.setShopPhone("13900000000");
        config.setShopAddress("测试门店地址");
        config.setHeaderMsg("欢迎光临");
        config.setFooterMsg("测试页脚");
        config.setAutoPrint(false);
        config.setOpenDrawer(false);
        assertThat(printConfigMapper.updateById(config)).isEqualTo(1);
        assertThat(printConfigMapper.selectById(1L))
                .extracting(SysPrintConfig::getId,
                        SysPrintConfig::getShopName,
                        SysPrintConfig::getShopPhone,
                        SysPrintConfig::getShopAddress,
                        SysPrintConfig::getHeaderMsg,
                        SysPrintConfig::getFooterMsg,
                        SysPrintConfig::getAutoPrint,
                        SysPrintConfig::getOpenDrawer)
                .containsExactly(1L, "AD-2.29 配置回归", "13900000000", "测试门店地址", "欢迎光临", "测试页脚", false, false);
    }
}
