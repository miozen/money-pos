package com.money.feature.trade.infrastructure.persistence.mapper;

import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderLog;
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
class OmsOrderLogMapperIntegrationTest {

    @Autowired
    private OmsOrderLogMapper orderLogMapper;

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
    void mapperInsertAndReadBackUseTheTradeLocalEntityAndAuditFills() {
        OmsOrderLog orderLog = new OmsOrderLog();
        orderLog.setOrderId(90000001L);
        orderLog.setDescription("AD-221 audit log");
        orderLog.setTenantId(0L);

        assertThat(orderLogMapper.insert(orderLog)).isEqualTo(1);
        assertThat(orderLog.getId()).isNotNull();
        assertThat(orderLogMapper.selectById(orderLog.getId()))
                .satisfies(persisted -> {
                    assertThat(persisted.getOrderId()).isEqualTo(90000001L);
                    assertThat(persisted.getDescription()).isEqualTo("AD-221 audit log");
                    assertThat(persisted.getTenantId()).isEqualTo(0L);
                    assertThat(persisted.getCreateBy()).isNotNull();
                    assertThat(persisted.getCreateTime()).isNotNull();
                    assertThat(persisted.getUpdateBy()).isNotNull();
                    assertThat(persisted.getUpdateTime()).isNotNull();
                });
    }
}
