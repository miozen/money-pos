package com.money.feature.trade.application.membertarget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.member.MemberBrandBenefitLedgerCommand;
import com.money.contract.member.MemberBrandBenefitLedgerCommandHandler;
import com.money.dto.pos.MemberTargetAdjustmentDTO;
import com.money.dto.pos.MemberTargetConfirmDTO;
import com.money.dto.pos.MemberTargetSettleDTO;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.trade.application.refund.OmsOrderRefundService;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetReceipt;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberTargetSaleContribution;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetReceiptMapper;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberTargetSaleContributionMapper;
import com.money.feature.ums.infrastructure.persistence.entity.UmsBrandBenefitTier;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberTargetPlan;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsBrandBenefitTierMapper;
import com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberTargetPlanMapper;
import com.money.mapper.GmsGoodsMapper;
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
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberTargetBenefitServiceIntegrationTest {
    @Autowired private MemberTargetBenefitService service;
    @Autowired private OmsOrderRefundService refundService;
    @Autowired private MemberBrandBenefitLedgerCommandHandler ledger;
    @Autowired private TradeFixture fixture;
    @Autowired private GmsGoodsMapper goodsMapper;
    @Autowired private UmsBrandBenefitTierMapper tierMapper;
    @Autowired private UmsMemberTargetPlanMapper planMapper;
    @Autowired private OmsMemberTargetSaleContributionMapper contributionMapper;
    @Autowired private OmsMemberTargetReceiptMapper receiptMapper;

    @BeforeEach void auth() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A")); MockHttpServletRequest r = new MockHttpServletRequest(); r.addHeader("Y-tenant", "0"); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(r)); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void targetSaleUsesNormalCheckoutThenRequiresManualConfirmationAndRefundReview() {
        String suffix = Long.toString(System.nanoTime(), 36); String brand = "1";
        GmsGoods goods = fixture.createSellableGoods(suffix, 3, new BigDecimal("12.00")); goods.setBrandId(1L); goodsMapper.updateById(goods);
        com.money.feature.ums.infrastructure.persistence.entity.UmsMember member = fixture.createMember(suffix, BigDecimal.ZERO);
        UmsBrandBenefitTier tier = new UmsBrandBenefitTier(); tier.setBrandId(brand); tier.setTierCode("T" + suffix); tier.setTierName("Target"); tier.setConfiguredAmount(new BigDecimal("10.00")); tier.setPricingLevelCode("TARGET-L"); tier.setRankValue(1); tier.setEnabled(true); tier.setSortNo(1); tierMapper.insert(tier);
        MemberBrandBenefitLedgerCommand.TargetPlanCreate create = new MemberBrandBenefitLedgerCommand.TargetPlanCreate(); create.setMemberId(member.getId()); create.setBrandId(brand); create.setTargetTierCode(tier.getTierCode()); create.setRequestNo("PLAN-" + suffix); create.setSourceType("TEST"); Long planId = ledger.createTargetPlan(create);
        SettleAccountsDTO settle = fixture.cashSettlement("SALE-" + suffix, goods.getId(), 1, new BigDecimal("12.00")); settle.setMember(member.getId()); MemberTargetSettleDTO target = new MemberTargetSettleDTO(); target.setTargetPlanId(planId); target.setSettle(settle);
        service.settle(target); service.settle(target);
        assertThat(contributionMapper.selectCount(new LambdaQueryWrapper<OmsMemberTargetSaleContribution>().eq(OmsMemberTargetSaleContribution::getTargetPlanId, planId))).isEqualTo(1);
        assertThat(planMapper.selectById(planId).getStatus()).isEqualTo("IN_PROGRESS");
        MemberTargetConfirmDTO confirm = new MemberTargetConfirmDTO(); confirm.setTargetPlanId(planId); confirm.setReqId("CONFIRM-" + suffix); service.confirm(confirm);
        assertThat(planMapper.selectById(planId).getStatus()).isEqualTo("CONFIRMED");
        refundService.returnOrder("REFUND-" + suffix, settle.getReqId());
        assertThat(planMapper.selectById(planId).getStatus()).isEqualTo("REVIEW_REQUIRED");
    }

    @Test void supplementIsNonProductReceiptAndWaiverHasNoPayment() {
        String suffix = Long.toString(System.nanoTime(), 36); UmsBrandBenefitTier tier = new UmsBrandBenefitTier(); tier.setBrandId("1"); tier.setTierCode("T" + suffix); tier.setTierName("Target"); tier.setConfiguredAmount(new BigDecimal("100.00")); tier.setPricingLevelCode("TARGET-L"); tier.setRankValue(1); tier.setEnabled(true); tier.setSortNo(1); tierMapper.insert(tier);
        com.money.feature.ums.infrastructure.persistence.entity.UmsMember member = fixture.createMember(suffix, BigDecimal.ZERO); MemberBrandBenefitLedgerCommand.TargetPlanCreate create = new MemberBrandBenefitLedgerCommand.TargetPlanCreate(); create.setMemberId(member.getId()); create.setBrandId("1"); create.setTargetTierCode(tier.getTierCode()); create.setRequestNo("PLAN-" + suffix); Long planId = ledger.createTargetPlan(create);
        MemberTargetAdjustmentDTO supplement = adjustment(planId, "SUP-" + suffix, "20.00", true); service.supplement(supplement); MemberTargetAdjustmentDTO waiver = adjustment(planId, "WAI-" + suffix, "5.00", false); service.waive(waiver);
        assertThat(receiptMapper.selectList(null)).extracting(OmsMemberTargetReceipt::getReceiptType).containsExactlyInAnyOrder("SUPPLEMENT", "WAIVER");
        assertThat(planMapper.selectById(planId).getProgressAmount()).isEqualByComparingTo("25.00");
    }

    private MemberTargetAdjustmentDTO adjustment(Long planId, String request, String amount, boolean paid) { MemberTargetAdjustmentDTO dto = new MemberTargetAdjustmentDTO(); dto.setTargetPlanId(planId); dto.setReqId(request); dto.setAmount(new BigDecimal(amount)); if (paid) { SettleAccountsDTO.PaymentItem pay = new SettleAccountsDTO.PaymentItem(); pay.setPayMethodCode("CASH"); pay.setPayMethodName("Cash"); pay.setPayAmount(new BigDecimal(amount)); dto.setPayments(Collections.singletonList(pay)); } return dto; }
}
