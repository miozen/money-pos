package com.money.feature.gms.infrastructure.persistence.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryOrderDetail;
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
class GmsInventoryOrderDetailMapperIntegrationTest {

    @Autowired
    private GmsInventoryOrderDetailMapper inventoryOrderDetailMapper;

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
    void mapperCrudUsesTheGmsLocalEntityWithImplicitTableMappingAndTenantFields() {
        GmsInventoryOrderDetail detail = new GmsInventoryOrderDetail();
        detail.setOrderId(90000001L);
        detail.setGoodsId(90000002L);
        detail.setQty(3);
        detail.setPrice(new BigDecimal("12.34"));
        detail.setCreateTime(LocalDateTime.of(2025, 1, 2, 3, 4, 5));
        detail.setTenantId(0L);

        assertThat(inventoryOrderDetailMapper.insert(detail)).isEqualTo(1);
        assertThat(detail.getId()).isNotNull();
        assertThat(inventoryOrderDetailMapper.selectById(detail.getId()))
                .extracting(GmsInventoryOrderDetail::getOrderId,
                        GmsInventoryOrderDetail::getGoodsId,
                        GmsInventoryOrderDetail::getQty,
                        GmsInventoryOrderDetail::getPrice,
                        GmsInventoryOrderDetail::getCreateTime,
                        GmsInventoryOrderDetail::getTenantId)
                .containsExactly(90000001L, 90000002L, 3, new BigDecimal("12.34"),
                        LocalDateTime.of(2025, 1, 2, 3, 4, 5), 0L);

        detail.setQty(8);
        detail.setPrice(new BigDecimal("56.78"));
        assertThat(inventoryOrderDetailMapper.updateById(detail)).isEqualTo(1);
        assertThat(inventoryOrderDetailMapper.selectById(detail.getId()))
                .extracting(GmsInventoryOrderDetail::getQty, GmsInventoryOrderDetail::getPrice)
                .containsExactly(8, new BigDecimal("56.78"));

        assertThat(inventoryOrderDetailMapper.deleteById(detail.getId())).isEqualTo(1);
        assertThat(inventoryOrderDetailMapper.selectById(detail.getId())).isNull();
    }
}
