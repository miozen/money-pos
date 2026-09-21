package com.money.feature.trade.infrastructure.persistence.mapper;

import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.mapper.OmsOrderMapper;
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
class OmsOrderMapperIntegrationTest {

    @Autowired
    private OmsOrderMapper omsOrderMapper;

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
    void mapperCrudUsesTheTradeLocalEntityAndAllOrderSnapshots() {
        String suffix = Long.toString(System.nanoTime(), 36);
        LocalDateTime paymentTime = LocalDateTime.of(2026, 9, 21, 10, 20, 30);
        LocalDateTime completionTime = paymentTime.plusMinutes(2);
        OmsOrder order = new OmsOrder();
        order.setOrderNo("AD259-" + suffix);
        order.setMember("member " + suffix);
        order.setMemberId(90000001L);
        order.setVip(true);
        order.setStatus("PAID");
        order.setContact("13800138000");
        order.setProvince("province");
        order.setCity("city");
        order.setDistrict("district");
        order.setAddress("address " + suffix);
        order.setCostAmount(new BigDecimal("30.01"));
        order.setRetailAmount(new BigDecimal("100.02"));
        order.setMemberAmount(new BigDecimal("80.03"));
        order.setPrivilegeAmount(new BigDecimal("19.99"));
        order.setActualCouponDeduct(new BigDecimal("5.04"));
        order.setWaivedCouponAmount(new BigDecimal("14.95"));
        order.setTotalAmount(new BigDecimal("100.02"));
        order.setCouponAmount(new BigDecimal("5.04"));
        order.setPayAmount(new BigDecimal("75.00"));
        order.setFinalSalesAmount(new BigDecimal("75.00"));
        order.setRemark("remark " + suffix);
        order.setPaymentTime(paymentTime);
        order.setCompletionTime(completionTime);
        order.setTenantId(0L);
        order.setUseVoucherAmount(new BigDecimal("3.00"));
        order.setManualDiscountAmount(new BigDecimal("2.00"));

        assertThat(omsOrderMapper.insert(order)).isEqualTo(1);
        assertThat(order.getId()).isNotNull();
        assertThat(omsOrderMapper.selectById(order.getId()))
                .extracting(OmsOrder::getOrderNo, OmsOrder::getMember, OmsOrder::getMemberId, OmsOrder::getVip,
                        OmsOrder::getStatus, OmsOrder::getContact, OmsOrder::getProvince, OmsOrder::getCity,
                        OmsOrder::getDistrict, OmsOrder::getAddress, OmsOrder::getCostAmount, OmsOrder::getRetailAmount,
                        OmsOrder::getMemberAmount, OmsOrder::getPrivilegeAmount, OmsOrder::getActualCouponDeduct,
                        OmsOrder::getWaivedCouponAmount, OmsOrder::getTotalAmount, OmsOrder::getCouponAmount,
                        OmsOrder::getPayAmount, OmsOrder::getFinalSalesAmount, OmsOrder::getRemark,
                        OmsOrder::getPaymentTime, OmsOrder::getCompletionTime, OmsOrder::getTenantId,
                        OmsOrder::getUseVoucherAmount, OmsOrder::getManualDiscountAmount)
                .containsExactly(order.getOrderNo(), order.getMember(), 90000001L, true, "PAID", order.getContact(),
                        "province", "city", "district", order.getAddress(), new BigDecimal("30.01"),
                        new BigDecimal("100.02"), new BigDecimal("80.03"), new BigDecimal("19.99"),
                        new BigDecimal("5.04"), new BigDecimal("14.95"), new BigDecimal("100.02"),
                        new BigDecimal("5.04"), new BigDecimal("75.00"), new BigDecimal("75.00"), order.getRemark(),
                        paymentTime, completionTime, 0L, new BigDecimal("3.00"), new BigDecimal("2.00"));

        order.setStatus("PARTIAL_REFUNDED");
        order.setFinalSalesAmount(new BigDecimal("50.00"));
        assertThat(omsOrderMapper.updateById(order)).isEqualTo(1);
        assertThat(omsOrderMapper.selectById(order.getId()))
                .extracting(OmsOrder::getStatus, OmsOrder::getFinalSalesAmount)
                .containsExactly("PARTIAL_REFUNDED", new BigDecimal("50.00"));

        assertThat(omsOrderMapper.deleteById(order.getId())).isEqualTo(1);
        assertThat(omsOrderMapper.selectById(order.getId())).isNull();
    }
}
