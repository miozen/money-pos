package com.money.mapper;

import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderPay;
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
class OmsOrderPayMapperIntegrationTest {

    @Autowired
    private OmsOrderPayMapper orderPayMapper;

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
    void mapperCrudUsesTheTradeLocalEntityWithImplicitTableAndPaymentSnapshots() {
        String suffix = Long.toString(System.nanoTime(), 36);
        LocalDateTime createTime = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        OmsOrderPay payment = new OmsOrderPay();
        payment.setOrderNo("AD215-" + suffix);
        payment.setPayMethodName("现金");
        payment.setPayTag("CASH");
        payment.setPayMethodCode("CASH");
        payment.setPayAmount(new BigDecimal("12.34"));
        payment.setOriginalAmount(new BigDecimal("15.00"));
        payment.setNetAmount(new BigDecimal("12.34"));
        payment.setChangeAllocated(new BigDecimal("2.66"));
        payment.setCreateTime(createTime);

        assertThat(orderPayMapper.insert(payment)).isEqualTo(1);
        assertThat(payment.getId()).isNotNull();
        assertThat(orderPayMapper.selectById(payment.getId()))
                .extracting(OmsOrderPay::getOrderNo,
                        OmsOrderPay::getPayMethodName,
                        OmsOrderPay::getPayTag,
                        OmsOrderPay::getPayMethodCode,
                        OmsOrderPay::getPayAmount,
                        OmsOrderPay::getOriginalAmount,
                        OmsOrderPay::getNetAmount,
                        OmsOrderPay::getChangeAllocated,
                        OmsOrderPay::getCreateTime)
                .containsExactly(payment.getOrderNo(), "现金", "CASH", "CASH", new BigDecimal("12.34"),
                        new BigDecimal("15.00"), new BigDecimal("12.34"), new BigDecimal("2.66"), createTime);

        payment.setPayMethodName("现金更新");
        payment.setPayAmount(new BigDecimal("10.00"));
        payment.setNetAmount(new BigDecimal("10.00"));
        payment.setChangeAllocated(new BigDecimal("5.00"));
        assertThat(orderPayMapper.updateById(payment)).isEqualTo(1);
        assertThat(orderPayMapper.selectById(payment.getId()))
                .extracting(OmsOrderPay::getPayMethodName,
                        OmsOrderPay::getPayAmount,
                        OmsOrderPay::getNetAmount,
                        OmsOrderPay::getChangeAllocated)
                .containsExactly("现金更新", new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));

        assertThat(orderPayMapper.deleteById(payment.getId())).isEqualTo(1);
        assertThat(orderPayMapper.selectById(payment.getId())).isNull();
    }
}
