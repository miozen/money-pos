package com.money.feature.trade.infrastructure.persistence.mapper;

import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.mapper.OmsOrderDetailMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class OmsOrderDetailMapperIntegrationTest {

    @Autowired
    private OmsOrderDetailMapper omsOrderDetailMapper;

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
    void mapperCrudUsesTheTradeLocalEntityAndAllOrderDetailSnapshots() {
        String suffix = Long.toString(System.nanoTime(), 36);
        OmsOrderDetail detail = new OmsOrderDetail();
        detail.setOrderNo("AD255-" + suffix);
        detail.setStatus("PAID");
        detail.setGoodsId(90000001L);
        detail.setGoodsBarcode("BAR-" + suffix);
        detail.setGoodsName("order detail " + suffix);
        detail.setGoodsPrice(new BigDecimal("12.34"));
        detail.setQuantity(3);
        detail.setSalePrice(new BigDecimal("15.00"));
        detail.setPurchasePrice(new BigDecimal("6.78"));
        detail.setVipPrice(new BigDecimal("11.11"));
        detail.setCoupon(new BigDecimal("2.22"));
        detail.setReturnQuantity(1);
        detail.setTenantId(0L);
        detail.setBrandId(90000002L);
        detail.setCategoryId(90000003L);
        detail.setCategoryName("category " + suffix);

        assertThat(omsOrderDetailMapper.insert(detail)).isEqualTo(1);
        assertThat(detail.getId()).isNotNull();
        assertThat(omsOrderDetailMapper.selectById(detail.getId()))
                .extracting(OmsOrderDetail::getOrderNo, OmsOrderDetail::getStatus, OmsOrderDetail::getGoodsId,
                        OmsOrderDetail::getGoodsBarcode, OmsOrderDetail::getGoodsName, OmsOrderDetail::getGoodsPrice,
                        OmsOrderDetail::getQuantity, OmsOrderDetail::getSalePrice, OmsOrderDetail::getPurchasePrice,
                        OmsOrderDetail::getVipPrice, OmsOrderDetail::getCoupon, OmsOrderDetail::getReturnQuantity,
                        OmsOrderDetail::getTenantId, OmsOrderDetail::getBrandId, OmsOrderDetail::getCategoryId,
                        OmsOrderDetail::getCategoryName)
                .containsExactly(detail.getOrderNo(), "PAID", 90000001L, detail.getGoodsBarcode(), detail.getGoodsName(),
                        new BigDecimal("12.34"), 3, new BigDecimal("15.00"), new BigDecimal("6.78"),
                        new BigDecimal("11.11"), new BigDecimal("2.22"), 1, 0L, 90000002L, 90000003L,
                        detail.getCategoryName());

        detail.setReturnQuantity(2);
        detail.setStatus("PARTIAL_REFUNDED");
        assertThat(omsOrderDetailMapper.updateById(detail)).isEqualTo(1);
        assertThat(omsOrderDetailMapper.selectById(detail.getId()))
                .extracting(OmsOrderDetail::getReturnQuantity, OmsOrderDetail::getStatus)
                .containsExactly(2, "PARTIAL_REFUNDED");

        assertThat(omsOrderDetailMapper.deleteById(detail.getId())).isEqualTo(1);
        assertThat(omsOrderDetailMapper.selectById(detail.getId())).isNull();
    }
}
