package com.money.feature.ums.interfaces.rest;

import com.money.feature.ums.infrastructure.persistence.entity.UmsRechargeOrder;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberAssetControllerIntegrationTest {

    @Autowired
    private UmsMemberAssetController memberAssetController;
    @Autowired
    private UmsRechargeOrderMapper rechargeOrderMapper;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ums/member/recharge/order/test");
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void queryRouteReturnsTheUmsLocalCertificateAndKeepsExistingFailures() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsRechargeOrder order = new UmsRechargeOrder();
        order.setOrderNo("AD227-ROUTE-" + suffix);
        order.setMemberId(90000002L);
        order.setType("BALANCE");
        order.setAmount(new BigDecimal("18.00"));
        order.setGiftCoupon(new BigDecimal("2.00"));
        order.setRealAmount(new BigDecimal("18.00"));
        order.setStatus("PAID");
        order.setRemark("route certificate " + suffix);
        order.setTenantId("0");
        order.setCreateTime(LocalDateTime.of(2025, 1, 2, 3, 4, 5));
        rechargeOrderMapper.insert(order);

        UmsRechargeOrder returned = controllerTarget().getRechargeOrderDetail(order.getOrderNo());

        assertThat(returned).extracting(UmsRechargeOrder::getId,
                        UmsRechargeOrder::getOrderNo,
                        UmsRechargeOrder::getMemberId,
                        UmsRechargeOrder::getType,
                        UmsRechargeOrder::getAmount,
                        UmsRechargeOrder::getGiftCoupon,
                        UmsRechargeOrder::getRealAmount,
                        UmsRechargeOrder::getStatus,
                        UmsRechargeOrder::getRemark,
                        UmsRechargeOrder::getTenantId,
                        UmsRechargeOrder::getCreateTime)
                .containsExactly(order.getId(), order.getOrderNo(), order.getMemberId(), "BALANCE",
                        new BigDecimal("18.00"), new BigDecimal("2.00"), new BigDecimal("18.00"), "PAID",
                        order.getRemark(), "0", order.getCreateTime());
        assertThatThrownBy(() -> controllerTarget().getRechargeOrderDetail(" "))
                .isInstanceOf(BaseException.class)
                .hasMessage("单号不能为空");
        assertThatThrownBy(() -> controllerTarget().getRechargeOrderDetail("missing-" + suffix))
                .isInstanceOf(BaseException.class)
                .hasMessage("单据档案不存在，可能已被物理删除");
    }

    private UmsMemberAssetController controllerTarget() throws Exception {
        return AopTestUtils.getTargetObject(memberAssetController);
    }
}
