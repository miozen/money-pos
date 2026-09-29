package com.money.feature.trade.application.memberpickup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.pos.*;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberAmountReceipt;
import com.money.feature.ums.infrastructure.persistence.entity.UmsBrandBenefitTier;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberAmountRight;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.support.TradeFixture;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.*;
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
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberAmountBenefitServiceIntegrationTest {
    @Autowired private MemberAmountBenefitService service;
    @Autowired private TradeFixture fixture;
    @Autowired private GmsGoodsMapper goodsMapper;
    @Autowired private PosSkuLevelPriceMapper levelPriceMapper;
    @Autowired private GmsInventoryDocMapper inventoryDocMapper;
    @Autowired private OmsOrderMapper orderMapper;
    @Autowired private com.money.feature.ums.infrastructure.persistence.mapper.UmsBrandBenefitTierMapper tierMapper;
    @Autowired private com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberAmountRightMapper rightMapper;
    @Autowired private com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberAmountReceiptMapper receiptMapper;

    @BeforeEach void auth() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A")); MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("Y-tenant", "0"); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void packagePurchaseIsIdempotentAndDoesNotCreateOrderOrMoveStock() {
        Setup s = setup(5);
        MemberAmountPackagePurchaseDTO request = purchaseRequest("BUY-" + s.suffix, s.member.getId(), s.brand, s.tierCode, new BigDecimal("100"));
        MemberAmountPackagePurchaseVO first = service.purchase(request); MemberAmountPackagePurchaseVO replay = service.purchase(request);
        assertThat(replay.getReceiptNo()).isEqualTo(first.getReceiptNo());
        assertThat(rightMapper.selectById(first.getAmountRightId()).getRemainingAmount()).isEqualByComparingTo("100");
        assertThat(goodsMapper.selectById(s.goods.getId()).getStock()).isEqualTo(5L);
        assertThat(orderMapper.selectCount(null)).isZero();
        assertThat(receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>().eq(OmsMemberAmountReceipt::getReceiptNo, first.getReceiptNo())).getReceiptType()).isEqualTo("AMOUNT_PACKAGE_PURCHASE");
    }

    @Test void pickupUsesFrozenTierPriceDeductsRightAndOnlyRecordsSupplement() {
        Setup s = setup(5); MemberAmountPackagePurchaseVO right = service.purchase(purchaseRequest("BUY-" + s.suffix, s.member.getId(), s.brand, s.tierCode, new BigDecimal("100")));
        MemberAmountPickupVO pickup = service.pickup(pickupRequest("PICK-" + s.suffix, s.member.getId(), right.getAmountRightId(), s.goods.getId(), 1, new BigDecimal("50")));
        assertThat(pickup).extracting(MemberAmountPickupVO::getRightDeductAmount, MemberAmountPickupVO::getSupplementAmount).containsExactly(new BigDecimal("100.00"), new BigDecimal("50.00"));
        assertThat(rightMapper.selectById(right.getAmountRightId()).getRemainingAmount()).isEqualByComparingTo("0");
        assertThat(goodsMapper.selectById(s.goods.getId()).getStock()).isEqualTo(4L);
        OmsMemberAmountReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>().eq(OmsMemberAmountReceipt::getReceiptNo, pickup.getSupplementReceiptNo()));
        assertThat(receipt).extracting(OmsMemberAmountReceipt::getReceiptType, OmsMemberAmountReceipt::getTotalAmount, OmsMemberAmountReceipt::getSupplementAmount).containsExactly("AMOUNT_PICKUP_SUPPLEMENT", new BigDecimal("150.00"), new BigDecimal("50.00"));
        assertThat(orderMapper.selectCount(null)).isZero();
        assertThat(inventoryDocMapper.selectOne(new LambdaQueryWrapper<GmsInventoryDoc>().eq(GmsInventoryDoc::getDocNo, "MP-" + pickup.getPickupNo())).getDocType()).isEqualTo("MEMBER_PICKUP");
    }

    @Test void fullPickupRefundRestoresRightStockAndSupplementExactlyOnce() {
        Setup s = setup(5); MemberAmountPackagePurchaseVO right = service.purchase(purchaseRequest("BUY-" + s.suffix, s.member.getId(), s.brand, s.tierCode, new BigDecimal("100")));
        MemberAmountPickupVO pickup = service.pickup(pickupRequest("PICK-" + s.suffix, s.member.getId(), right.getAmountRightId(), s.goods.getId(), 1, new BigDecimal("50")));
        MemberAmountPickupRefundDTO refund = new MemberAmountPickupRefundDTO(); refund.setPickupNo(pickup.getPickupNo()); refund.setReqId("REFUND-" + s.suffix);
        service.refund(refund);
        assertThat(rightMapper.selectById(right.getAmountRightId()).getRemainingAmount()).isEqualByComparingTo("100");
        assertThat(goodsMapper.selectById(s.goods.getId()).getStock()).isEqualTo(5L);
        assertThat(receiptMapper.selectOne(new LambdaQueryWrapper<OmsMemberAmountReceipt>().eq(OmsMemberAmountReceipt::getReceiptNo, pickup.getSupplementReceiptNo())).getStatus()).isEqualTo("REFUNDED");
        assertThat(inventoryDocMapper.selectCount(new LambdaQueryWrapper<GmsInventoryDoc>().eq(GmsInventoryDoc::getDocType, "MEMBER_PICKUP_RETURN"))).isEqualTo(1);
        assertThatThrownBy(() -> service.refund(refund)).isInstanceOf(BaseException.class).hasMessageContaining("不能重复退款");
    }

    @Test void insufficientPhysicalStockRejectsAmountPickup() {
        Setup s = setup(0); MemberAmountPackagePurchaseVO right = service.purchase(purchaseRequest("BUY-" + s.suffix, s.member.getId(), s.brand, s.tierCode, new BigDecimal("100")));
        assertThatThrownBy(() -> service.pickup(pickupRequest("PICK-" + s.suffix, s.member.getId(), right.getAmountRightId(), s.goods.getId(), 1, new BigDecimal("50")))).isInstanceOf(BaseException.class);
        assertThat(goodsMapper.selectById(s.goods.getId()).getStock()).isZero();
    }

    private Setup setup(long stock) { String suffix = Long.toString(System.nanoTime(), 36), brand = "1"; UmsMember member = fixture.createMember(suffix, BigDecimal.ZERO); GmsGoods goods = fixture.createSellableGoods(suffix, stock, new BigDecimal("180")); goods.setBrandId(1L); goodsMapper.updateById(goods); UmsBrandBenefitTier tier = new UmsBrandBenefitTier(); tier.setBrandId(brand); tier.setTierCode("T" + suffix); tier.setTierName("Tier"); tier.setConfiguredAmount(new BigDecimal("100")); tier.setPricingLevelCode("AMT-L"); tier.setRankValue(1); tier.setEnabled(true); tier.setSortNo(1); tierMapper.insert(tier); PosSkuLevelPrice price = new PosSkuLevelPrice(); price.setSkuId(goods.getId()); price.setLevelId("AMT-L"); price.setMemberPrice(new BigDecimal("150")); price.setMemberCoupon(BigDecimal.ZERO); price.setTenantId("0"); levelPriceMapper.insert(price); return new Setup(suffix, brand, member, goods, tier.getTierCode()); }
    private MemberAmountPackagePurchaseDTO purchaseRequest(String req, Long member, String brand, String tier, BigDecimal amount) { MemberAmountPackagePurchaseDTO dto = new MemberAmountPackagePurchaseDTO(); dto.setReqId(req); dto.setMemberId(member); dto.setBrandId(brand); dto.setTierCode(tier); dto.setPayments(Collections.singletonList(payment(amount))); return dto; }
    private MemberAmountPickupDTO pickupRequest(String req, Long member, Long right, Long goods, int quantity, BigDecimal supplement) { MemberAmountPickupDTO dto = new MemberAmountPickupDTO(); dto.setReqId(req); dto.setMemberId(member); dto.setAmountRightId(right); MemberAmountPickupDTO.Line line = new MemberAmountPickupDTO.Line(); line.setGoodsId(goods); line.setQuantity(quantity); dto.setLines(Collections.singletonList(line)); dto.setPayments(Collections.singletonList(payment(supplement))); return dto; }
    private SettleAccountsDTO.PaymentItem payment(BigDecimal amount) { SettleAccountsDTO.PaymentItem p = new SettleAccountsDTO.PaymentItem(); p.setPayMethodCode("AGGREGATE"); p.setPayMethodName("Wechat"); p.setPayTag("WECHAT"); p.setPayAmount(amount); return p; }
    private static class Setup { final String suffix, brand, tierCode; final UmsMember member; final GmsGoods goods; Setup(String s, String b, UmsMember m, GmsGoods g, String t) { suffix=s; brand=b; member=m; goods=g; tierCode=t; } }
}
