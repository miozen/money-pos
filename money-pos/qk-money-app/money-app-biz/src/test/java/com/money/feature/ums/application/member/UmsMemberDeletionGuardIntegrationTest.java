package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.support.TradeFixture;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberDeletionGuardIntegrationTest {

    @Autowired private UmsMemberProfileService profileService;
    @Autowired private MemberMergePreviewService memberMergePreviewService;
    @Autowired private TradeFixture tradeFixture;
    @Autowired private UmsMemberMapper memberMapper;
    @Autowired private OmsOrderMapper orderMapper;
    @Autowired private PosMemberCouponMapper couponMapper;
    @Autowired private UmsMemberBrandLevelMapper brandLevelMapper;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
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
    void deletesOnlyACompletelyEmptyErroneousProfileWithoutRewritingItsFields() {
        UmsMember member = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        String originalName = member.getName();

        profileService.delete(Set.of(member.getId()));

        UmsMember deleted = memberMapper.selectById(member.getId());
        assertThat(deleted.getDeleted()).isTrue();
        assertThat(deleted.getName()).isEqualTo(originalName);
        assertThat(deleted.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(deleted.getCoupon()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsDeletionWhenAnyBusinessTraceExistsAndBatchHasNoPartialSuccess() {
        UmsMember empty = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        UmsMember ordered = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        OmsOrder order = new OmsOrder();
        order.setOrderNo("DEL-" + unique());
        order.setMember("history");
        order.setMemberId(ordered.getId());
        order.setVip(true);
        order.setStatus("PAID");
        order.setContact(""); order.setProvince(""); order.setCity(""); order.setDistrict(""); order.setAddress("");
        order.setCostAmount(BigDecimal.ZERO); order.setTotalAmount(BigDecimal.ZERO); order.setPayAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO); order.setFinalSalesAmount(BigDecimal.ZERO); order.setRemark("");
        order.setPaymentTime(LocalDateTime.now());
        order.setTenantId(0L);
        orderMapper.insert(order);

        assertThatThrownBy(() -> profileService.delete(Set.of(empty.getId(), ordered.getId())))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("存在订单记录");

        assertThat(memberMapper.selectById(empty.getId()).getDeleted()).isFalse();
        assertThat(memberMapper.selectById(ordered.getId()).getDeleted()).isFalse();
    }

    @Test
    void rejectsBalancesCouponWalletAndBrandLevels() {
        UmsMember funded = tradeFixture.createMember(unique(), new BigDecimal("1.00"));
        UmsMember couponOwner = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        PosMemberCoupon coupon = new PosMemberCoupon();
        coupon.setMemberId(couponOwner.getId()); coupon.setRuleId(1L); coupon.setStatus("UNUSED"); coupon.setTenantId("0");
        couponMapper.insert(coupon);
        UmsMember levelOwner = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        UmsMemberBrandLevel level = new UmsMemberBrandLevel();
        level.setMemberId(levelOwner.getId()); level.setBrand("delete-guard"); level.setLevelCode("NORMAL"); level.setTenantId(0L);
        brandLevelMapper.insert(level);

        assertThatThrownBy(() -> profileService.delete(Set.of(funded.getId())))
                .hasMessageContaining("余额或券额度");
        assertThatThrownBy(() -> profileService.delete(Set.of(couponOwner.getId())))
                .hasMessageContaining("会员券记录");
        assertThatThrownBy(() -> profileService.delete(Set.of(levelOwner.getId())))
                .hasMessageContaining("品牌等级记录");
    }

    @Test
    void mergePreviewIsReadOnlyAndReportsCurrentTransferInputs() {
        UmsMember source = tradeFixture.createMember(unique(), new BigDecimal("12.00"));
        UmsMember target = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        PosMemberCoupon coupon = new PosMemberCoupon();
        coupon.setMemberId(source.getId()); coupon.setRuleId(1L); coupon.setStatus("UNUSED"); coupon.setTenantId("0");
        couponMapper.insert(coupon);

        com.money.dto.UmsMember.MemberMergePreviewVO preview = memberMergePreviewService.preview(source.getId(), target.getId());

        assertThat(preview.isMergeAllowed()).isTrue();
        assertThat(preview.getSource().getBalance()).isEqualByComparingTo("12.00");
        assertThat(preview.getSourceUnusedCouponCount()).isEqualTo(1);
        assertThat(memberMapper.selectById(source.getId()).getDeleted()).isFalse();
        assertThat(couponMapper.selectById(coupon.getId()).getMemberId()).isEqualTo(source.getId());
    }

    private String unique() {
        return Long.toString(System.nanoTime(), 36);
    }
}
