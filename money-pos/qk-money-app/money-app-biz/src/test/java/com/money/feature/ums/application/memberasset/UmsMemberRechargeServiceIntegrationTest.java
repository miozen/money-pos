package com.money.feature.ums.application.memberasset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.Ums.RechargeDTO;
import com.money.entity.UmsMember;
import com.money.entity.UmsMemberLog;
import com.money.feature.ums.infrastructure.persistence.entity.UmsRechargeOrder;
import com.money.mapper.UmsMemberLogMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsRechargeOrderMapper;
import com.money.support.TradeFixture;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberRechargeServiceIntegrationTest {

    @Autowired
    private UmsMemberRechargeService rechargeService;
    @Autowired
    private TradeFixture tradeFixture;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberLogMapper memberLogMapper;
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
    void balanceRechargeAndVoidKeepCertificateAssetsAndLogsInSync() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        RechargeDTO request = new RechargeDTO();
        request.setMemberId(member.getId());
        request.setType("BALANCE");
        request.setAmount(new BigDecimal("20.00"));
        request.setGiftCoupon(new BigDecimal("3.00"));
        request.setRealAmount(new BigDecimal("20.00"));
        request.setRemark("AD-2.27 recharge " + suffix);

        rechargeService.recharge(request);

        UmsRechargeOrder paid = rechargeOrderMapper.selectOne(new LambdaQueryWrapper<UmsRechargeOrder>()
                .eq(UmsRechargeOrder::getMemberId, member.getId())
                .eq(UmsRechargeOrder::getStatus, "PAID")
                .last("LIMIT 1"));
        assertThat(paid).isNotNull();
        assertThat(paid.getOrderNo()).startsWith("RC");
        assertThat(paid).extracting(UmsRechargeOrder::getType,
                        UmsRechargeOrder::getAmount,
                        UmsRechargeOrder::getGiftCoupon,
                        UmsRechargeOrder::getRealAmount,
                        UmsRechargeOrder::getRemark)
                .containsExactly("BALANCE", new BigDecimal("20.00"), new BigDecimal("3.00"),
                        new BigDecimal("20.00"), request.getRemark());
        assertThat(memberMapper.selectById(member.getId()))
                .extracting(UmsMember::getBalance, UmsMember::getCoupon)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("3.00"));
        assertThat(memberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .eq(UmsMemberLog::getOrderNo, paid.getOrderNo())))
                .extracting(UmsMemberLog::getType, UmsMemberLog::getOperateType)
                .contains(tuple("BALANCE", "RECHARGE"), tuple("COUPON", "GIFT"));

        rechargeService.voidRecharge(paid.getOrderNo(), "AD-2.27 red void");

        assertThat(rechargeOrderMapper.selectById(paid.getId()))
                .extracting(UmsRechargeOrder::getStatus, UmsRechargeOrder::getRemark)
                .containsExactly("VOID", request.getRemark() + " | 撤销原因：AD-2.27 red void");
        UmsMember voidedMember = memberMapper.selectById(member.getId());
        assertThat(voidedMember.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(voidedMember.getCoupon()).isEqualByComparingTo(BigDecimal.ZERO);
        List<UmsMemberLog> logs = memberLogMapper.selectList(new LambdaQueryWrapper<UmsMemberLog>()
                .eq(UmsMemberLog::getOrderNo, paid.getOrderNo()));
        assertThat(logs).extracting(UmsMemberLog::getType, UmsMemberLog::getOperateType)
                .contains(tuple("BALANCE", "REVERSAL"), tuple("COUPON", "REVERSAL"));
    }
}
