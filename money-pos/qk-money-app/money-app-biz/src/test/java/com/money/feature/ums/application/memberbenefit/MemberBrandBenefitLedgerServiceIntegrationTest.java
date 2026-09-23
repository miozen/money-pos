package com.money.feature.ums.application.memberbenefit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberBrandBenefitLedgerCommand;
import com.money.feature.ums.infrastructure.persistence.entity.*;
import com.money.feature.ums.infrastructure.persistence.mapper.*;
import com.money.mapper.UmsMemberBrandLevelMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberBrandBenefitLedgerServiceIntegrationTest {
    @Autowired private MemberBrandBenefitLedgerService service;
    @Autowired private UmsBrandBenefitTierMapper tierMapper;
    @Autowired private UmsMemberAmountRightMapper amountRightMapper;
    @Autowired private UmsMemberAmountRightLogMapper amountLogMapper;
    @Autowired private UmsMemberQuantityRightMapper quantityRightMapper;
    @Autowired private UmsMemberQuantityRightLogMapper quantityLogMapper;
    @Autowired private UmsMemberTargetPlanMapper targetPlanMapper;
    @Autowired private UmsMemberTargetProgressLogMapper targetLogMapper;
    @Autowired private UmsMemberBrandLevelMapper brandLevelMapper;

    @BeforeEach void auth() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void amountLedgerIsAuditedIdempotentAndCannotBecomeNegative() {
        String brand = "ME1-" + Long.toString(System.nanoTime(), 36); createTier(brand, "T660", "L660", 1, "660.00");
        MemberBrandBenefitLedgerCommand.AmountGrant grant = new MemberBrandBenefitLedgerCommand.AmountGrant();
        grant.setMemberId(91001L); grant.setBrandId(brand); grant.setTierCode("T660"); grant.setAmount(new BigDecimal("100"));
        grant.setSourceReceiptNo("R-" + brand); grant.setRequestNo("REQ-G-" + brand); grant.setOperatorName("tester");
        Long rightId = service.grantAmount(grant);
        assertThat(service.grantAmount(grant)).isEqualTo(rightId);
        MemberBrandBenefitLedgerCommand.AmountChange pickup = new MemberBrandBenefitLedgerCommand.AmountChange();
        pickup.setRightId(rightId); pickup.setDelta(new BigDecimal("-60")); pickup.setAction("PICKUP_DEDUCT");
        pickup.setRequestNo("REQ-P-" + brand); pickup.setSourceType("PICKUP"); pickup.setOperatorName("tester");
        service.changeAmount(pickup); service.changeAmount(pickup);
        assertThat(amountRightMapper.selectById(rightId).getRemainingAmount()).isEqualByComparingTo("40");
        assertThat(amountLogMapper.selectCount(new LambdaQueryWrapper<UmsMemberAmountRightLog>().eq(UmsMemberAmountRightLog::getRightId, rightId))).isEqualTo(2);
        pickup.setRequestNo("REQ-OVER-" + brand); pickup.setDelta(new BigDecimal("-41"));
        assertThatThrownBy(() -> service.changeAmount(pickup)).isInstanceOf(BaseException.class).hasMessageContaining("不足");
    }

    @Test void targetInitialProgressIsAnImmutableAuditEntryAndTierNeverDowngrades() {
        String brand = "ME1-" + Long.toString(System.nanoTime(), 36);
        createTier(brand, "T660", "L660", 1, "660.00"); createTier(brand, "T2700", "L2700", 2, "2700.00");
        MemberBrandBenefitLedgerCommand.TargetPlanCreate create = new MemberBrandBenefitLedgerCommand.TargetPlanCreate();
        create.setMemberId(91002L); create.setBrandId(brand); create.setTargetTierCode("T2700"); create.setInitialProgress(new BigDecimal("660"));
        create.setRequestNo("REQ-T-" + brand); create.setSourceType("HISTORY_INIT"); create.setSourceNo("INIT-1"); create.setOperatorName("tester");
        Long planId = service.createTargetPlan(create);
        assertThat(targetPlanMapper.selectById(planId).getProgressAmount()).isEqualByComparingTo("660");
        assertThat(targetLogMapper.selectOne(new LambdaQueryWrapper<UmsMemberTargetProgressLog>().eq(UmsMemberTargetProgressLog::getPlanId, planId)).getAction()).isEqualTo("INITIAL_PROGRESS");
        MemberBrandBenefitLedgerCommand.TargetProgressChange contribution = new MemberBrandBenefitLedgerCommand.TargetProgressChange();
        contribution.setPlanId(planId); contribution.setDelta(new BigDecimal("100")); contribution.setAction("SALE_CONTRIBUTION");
        contribution.setRequestNo("REQ-T-SALE-" + brand); contribution.setSourceType("TRADE_ORDER"); contribution.setSourceNo("O-1"); contribution.setOperatorName("tester");
        service.changeTargetProgress(contribution); service.changeTargetProgress(contribution);
        assertThat(targetPlanMapper.selectById(planId).getProgressAmount()).isEqualByComparingTo("760");
        service.activateTierIfHigher(91002L, brand, "T2700"); service.activateTierIfHigher(91002L, brand, "T660");
        assertThat(brandLevelMapper.selectOne(new LambdaQueryWrapper<UmsMemberBrandLevel>().eq(UmsMemberBrandLevel::getMemberId, 91002L).eq(UmsMemberBrandLevel::getBrand, brand)).getLevelCode()).isEqualTo("L2700");
    }

    @Test void quantityLedgerIsAuditedIdempotentAndCannotBecomeNegative() {
        String brand = "ME1-" + Long.toString(System.nanoTime(), 36);
        MemberBrandBenefitLedgerCommand.QuantityGrant grant = new MemberBrandBenefitLedgerCommand.QuantityGrant();
        grant.setMemberId(91003L); grant.setBrandId(brand); grant.setGoodsId(83001L); grant.setSourceOrderNo("O-" + brand);
        grant.setSourceOrderDetailId(1L); grant.setQuantity(5); grant.setRequestNo("REQ-Q-G-" + brand); grant.setOperatorName("tester");
        Long rightId = service.grantQuantity(grant);
        assertThat(service.grantQuantity(grant)).isEqualTo(rightId);
        MemberBrandBenefitLedgerCommand.QuantityChange pickup = new MemberBrandBenefitLedgerCommand.QuantityChange();
        pickup.setRightId(rightId); pickup.setDelta(-3); pickup.setPickedDelta(3); pickup.setAction("PICKUP_DEDUCT");
        pickup.setRequestNo("REQ-Q-P-" + brand); pickup.setSourceType("PICKUP"); pickup.setOperatorName("tester");
        service.changeQuantity(pickup); service.changeQuantity(pickup);
        assertThat(quantityRightMapper.selectById(rightId).getRemainingQuantity()).isEqualTo(2);
        assertThat(quantityRightMapper.selectById(rightId).getPickedQuantity()).isEqualTo(3);
        assertThat(quantityLogMapper.selectCount(new LambdaQueryWrapper<UmsMemberQuantityRightLog>().eq(UmsMemberQuantityRightLog::getRightId, rightId))).isEqualTo(2);
        pickup.setRequestNo("REQ-Q-OVER-" + brand); pickup.setDelta(-3); pickup.setPickedDelta(3);
        assertThatThrownBy(() -> service.changeQuantity(pickup)).isInstanceOf(BaseException.class).hasMessageContaining("不足");
    }

    private void createTier(String brand, String code, String pricingCode, int rank, String amount) {
        UmsBrandBenefitTier tier = new UmsBrandBenefitTier(); tier.setBrandId(brand); tier.setTierCode(code); tier.setTierName(code);
        tier.setPricingLevelCode(pricingCode); tier.setRankValue(rank); tier.setConfiguredAmount(new BigDecimal(amount)); tier.setEnabled(true); tier.setSortNo(rank);
        tierMapper.insert(tier);
    }
}
