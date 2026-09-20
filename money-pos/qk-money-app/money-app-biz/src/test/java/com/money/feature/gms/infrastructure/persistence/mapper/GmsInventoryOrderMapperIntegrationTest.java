package com.money.feature.gms.infrastructure.persistence.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryOrder;
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
class GmsInventoryOrderMapperIntegrationTest {

    @Autowired
    private GmsInventoryOrderMapper inventoryOrderMapper;

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
    void mapperCrudUsesTheGmsLocalEntityWithImplicitTableAndAssignedId() {
        String suffix = Long.toString(System.nanoTime(), 36);
        LocalDateTime createTime = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime updateTime = LocalDateTime.of(2025, 1, 2, 3, 4, 6);
        GmsInventoryOrder order = new GmsInventoryOrder();
        order.setOrderNo("AD223-" + suffix);
        order.setType("INBOUND");
        order.setTotalAmount(new BigDecimal("12.34"));
        order.setStatus("COMPLETED");
        order.setRemark("mapper regression");
        order.setCreateTime(createTime);
        order.setUpdateTime(updateTime);
        order.setTenantId(0L);

        assertThat(inventoryOrderMapper.insert(order)).isEqualTo(1);
        assertThat(order.getId()).isNotNull();
        assertThat(inventoryOrderMapper.selectById(order.getId()))
                .extracting(GmsInventoryOrder::getOrderNo,
                        GmsInventoryOrder::getType,
                        GmsInventoryOrder::getTotalAmount,
                        GmsInventoryOrder::getStatus,
                        GmsInventoryOrder::getRemark,
                        GmsInventoryOrder::getCreateTime,
                        GmsInventoryOrder::getUpdateTime,
                        GmsInventoryOrder::getTenantId)
                .containsExactly(order.getOrderNo(), "INBOUND", new BigDecimal("12.34"), "COMPLETED",
                        "mapper regression", createTime, updateTime, 0L);

        order.setStatus("PENDING");
        order.setRemark("mapper update");
        assertThat(inventoryOrderMapper.updateById(order)).isEqualTo(1);
        assertThat(inventoryOrderMapper.selectById(order.getId()))
                .extracting(GmsInventoryOrder::getStatus, GmsInventoryOrder::getRemark)
                .containsExactly("PENDING", "mapper update");

        assertThat(inventoryOrderMapper.deleteById(order.getId())).isEqualTo(1);
        assertThat(inventoryOrderMapper.selectById(order.getId())).isNull();
    }
}
