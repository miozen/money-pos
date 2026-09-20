package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.UmsRechargeOrder;
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
class UmsRechargeOrderMapperIntegrationTest {

    @Autowired
    private UmsRechargeOrderMapper rechargeOrderMapper;

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
    void mapperPersistsTheUmsLocalRechargeCertificateAndVoidUpdate() {
        String suffix = Long.toString(System.nanoTime(), 36);
        LocalDateTime createTime = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        UmsRechargeOrder order = new UmsRechargeOrder();
        order.setOrderNo("AD227-" + suffix);
        order.setMemberId(90000001L);
        order.setType("BALANCE");
        order.setAmount(new BigDecimal("12.34"));
        order.setGiftCoupon(new BigDecimal("5.67"));
        order.setRealAmount(new BigDecimal("12.34"));
        order.setStatus("PAID");
        order.setRemark("recharge certificate " + suffix);
        order.setTenantId("0");
        order.setCreateTime(createTime);

        assertThat(rechargeOrderMapper.insert(order)).isEqualTo(1);
        assertThat(order.getId()).isNotNull();
        assertThat(rechargeOrderMapper.selectById(order.getId()))
                .extracting(UmsRechargeOrder::getOrderNo,
                        UmsRechargeOrder::getMemberId,
                        UmsRechargeOrder::getType,
                        UmsRechargeOrder::getAmount,
                        UmsRechargeOrder::getGiftCoupon,
                        UmsRechargeOrder::getRealAmount,
                        UmsRechargeOrder::getStatus,
                        UmsRechargeOrder::getRemark,
                        UmsRechargeOrder::getTenantId,
                        UmsRechargeOrder::getCreateTime)
                .containsExactly(order.getOrderNo(), 90000001L, "BALANCE", new BigDecimal("12.34"),
                        new BigDecimal("5.67"), new BigDecimal("12.34"), "PAID", order.getRemark(), "0", createTime);

        order.setStatus("VOID");
        order.setRemark("recharge certificate voided " + suffix);
        assertThat(rechargeOrderMapper.updateById(order)).isEqualTo(1);
        assertThat(rechargeOrderMapper.selectById(order.getId()))
                .extracting(UmsRechargeOrder::getStatus, UmsRechargeOrder::getRemark)
                .containsExactly("VOID", order.getRemark());
    }
}
