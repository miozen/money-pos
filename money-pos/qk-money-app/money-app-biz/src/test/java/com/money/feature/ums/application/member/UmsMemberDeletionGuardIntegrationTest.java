package com.money.feature.ums.application.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberLog;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberMerge;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberMergeMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncOutboxMapper;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import com.money.dto.UmsMember.MemberMergeCommandDTO;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.UmsMemberLogMapper;
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
    @Autowired private MemberMergeCommandService memberMergeCommandService;
    @Autowired private TradeFixture tradeFixture;
    @Autowired private UmsMemberMapper memberMapper;
    @Autowired private OmsOrderMapper orderMapper;
    @Autowired private PosMemberCouponMapper couponMapper;
    @Autowired private UmsMemberBrandLevelMapper brandLevelMapper;
    @Autowired private UmsMemberMergeMapper memberMergeMapper;
    @Autowired private UmsMemberLogMapper memberLogMapper;
    @Autowired private CloudMemberSyncOutboxMapper cloudMemberSyncOutboxMapper;

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

    @Test
    void blocksAmountStyleCouponCreditInPreviewAndExecution() {
        UmsMember source = tradeFixture.createMember(unique(), BigDecimal.ZERO);
        source.setCoupon(new BigDecimal("3.00")); memberMapper.updateById(source);
        UmsMember target = tradeFixture.createMember(unique(), BigDecimal.ZERO);

        assertThat(memberMergePreviewService.preview(source.getId(), target.getId()).isMergeAllowed()).isFalse();
        assertThatThrownBy(() -> memberMergeCommandService.merge(command(source.getId(), target.getId(), "coupon")))
                .isInstanceOf(BaseException.class).hasMessageContaining("金额型券额度");
        assertThat(memberMapper.selectById(source.getId()).getDeleted()).isFalse();
    }

    @Test
    void mergesBalanceAndOnlyUnusedVouchersOnceWhilePreservingHistoricalVoucherRows() {
        UmsMember source = tradeFixture.createMember(unique(), new BigDecimal("12.00"));
        UmsMember target = tradeFixture.createMember(unique(), new BigDecimal("5.00"));
        PosMemberCoupon unused = coupon(source.getId(), "UNUSED");
        PosMemberCoupon used = coupon(source.getId(), "USED");
        MemberMergeCommandDTO command = command(source.getId(), target.getId(), "duplicate-import");

        memberMergeCommandService.merge(command);
        memberMergeCommandService.merge(command);

        UmsMember mergedSource = memberMapper.selectById(source.getId());
        UmsMember mergedTarget = memberMapper.selectById(target.getId());
        assertThat(mergedSource.getDeleted()).isTrue();
        assertThat(mergedSource.getMergedToMemberId()).isEqualTo(target.getId());
        assertThat(mergedSource.getBalance()).isEqualByComparingTo("0.00");
        assertThat(mergedTarget.getBalance()).isEqualByComparingTo("17.00");
        assertThat(couponMapper.selectById(unused.getId()).getMemberId()).isEqualTo(target.getId());
        assertThat(couponMapper.selectById(used.getId()).getMemberId()).isEqualTo(source.getId());
        assertThat(memberMergeMapper.selectCount(new LambdaQueryWrapper<UmsMemberMerge>().eq(UmsMemberMerge::getSourceMemberId, source.getId()))).isEqualTo(1);
        assertThat(memberLogMapper.selectCount(new LambdaQueryWrapper<UmsMemberLog>().eq(UmsMemberLog::getOrderNo, command.getRequestNo()).eq(UmsMemberLog::getOperateType, "MERGE"))).isEqualTo(2);
        assertThat(cloudMemberSyncOutboxMapper.selectOne(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .eq(CloudMemberSyncOutbox::getMemberId, source.getId())).getOperation()).isEqualTo("TOMBSTONE");
        assertThat(cloudMemberSyncOutboxMapper.selectOne(new LambdaQueryWrapper<CloudMemberSyncOutbox>()
                .eq(CloudMemberSyncOutbox::getMemberId, target.getId())).getOperation()).isEqualTo("UPSERT");
    }

    private MemberMergeCommandDTO command(Long sourceId, Long targetId, String reason) {
        MemberMergeCommandDTO command = new MemberMergeCommandDTO();
        command.setRequestNo("MERGE-" + unique()); command.setSourceMemberId(sourceId); command.setTargetMemberId(targetId); command.setReason(reason);
        return command;
    }

    private PosMemberCoupon coupon(Long memberId, String status) {
        PosMemberCoupon coupon = new PosMemberCoupon(); coupon.setMemberId(memberId); coupon.setRuleId(1L); coupon.setStatus(status); coupon.setTenantId("0"); couponMapper.insert(coupon); return coupon;
    }

    private String unique() {
        return Long.toString(System.nanoTime(), 36);
    }
}
