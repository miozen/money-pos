package com.money.feature.trade.application.memberpickup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.pos.DeferredQuantityPickupDTO;
import com.money.dto.pos.DeferredQuantityPickupPreviewDTO;
import com.money.dto.pos.DeferredQuantityPickupPreviewVO;
import com.money.dto.pos.DeferredQuantityPickupVO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
import com.money.feature.trade.application.checkout.CheckoutOrchestrator;
import com.money.feature.trade.application.refund.OmsOrderRefundService;
import com.money.feature.trade.infrastructure.persistence.entity.OmsMemberQuantityPickup;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberQuantityRight;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.GmsGoodsComboMapper;
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
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class DeferredQuantityPickupServiceIntegrationTest {
    @Autowired private DeferredQuantityPickupService pickupService;
    @Autowired private CheckoutOrchestrator checkoutOrchestrator;
    @Autowired private OmsOrderRefundService refundService;
    @Autowired private TradeFixture tradeFixture;
    @Autowired private GmsGoodsMapper goodsMapper;
    @Autowired private GmsInventoryDocMapper inventoryDocMapper;
    @Autowired private GmsGoodsComboMapper goodsComboMapper;
    @Autowired private com.money.feature.ums.infrastructure.persistence.mapper.UmsMemberQuantityRightMapper rightMapper;
    @Autowired private com.money.feature.trade.infrastructure.persistence.mapper.OmsMemberQuantityPickupMapper pickupMapper;

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
    void pickupConsumesRightAndStockAtomicallyAndIsRequestIdempotent() {
        Fixture fixture = purchaseRight(3, 5);
        DeferredQuantityPickupDTO request = pickupRequest("PICKUP-" + fixture.suffix, fixture.member.getId(), fixture.right.getId(), 3);

        DeferredQuantityPickupVO first = pickupService.pickup(request);
        DeferredQuantityPickupVO replay = pickupService.pickup(request);

        assertThat(replay.getPickupNo()).isEqualTo(first.getPickupNo());
        assertThat(goodsMapper.selectById(fixture.goods.getId()).getStock()).isEqualTo(2L);
        assertThat(rightMapper.selectById(fixture.right.getId()))
                .extracting(UmsMemberQuantityRight::getRemainingQuantity, UmsMemberQuantityRight::getPickedQuantity)
                .containsExactly(0, 3);
        assertThat(pickupMapper.selectList(new LambdaQueryWrapper<OmsMemberQuantityPickup>()
                .eq(OmsMemberQuantityPickup::getRequestNo, request.getReqId())))
                .singleElement().extracting(OmsMemberQuantityPickup::getPickupNo).isEqualTo(first.getPickupNo());
        assertThat(inventoryDocMapper.selectOne(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocNo, "MP-" + first.getPickupNo())))
                .extracting(GmsInventoryDoc::getDocType).isEqualTo("MEMBER_PICKUP");
    }

    @Test
    void previewShowsRemainingQuantityWithoutCreatingPickupOrMutatingRightOrStock() {
        Fixture fixture = purchaseRight(5, 8);
        DeferredQuantityPickupPreviewVO preview = pickupService.preview(previewRequest(fixture.member.getId(), fixture.right.getId(), 2));

        assertThat(preview.getLines()).singleElement().extracting(
                DeferredQuantityPickupPreviewVO.Line::getGoodsId,
                DeferredQuantityPickupPreviewVO.Line::getGrantedQuantity,
                DeferredQuantityPickupPreviewVO.Line::getPickedQuantity,
                DeferredQuantityPickupPreviewVO.Line::getPickupQuantity,
                DeferredQuantityPickupPreviewVO.Line::getRemainingAfterPickup)
                .containsExactly(fixture.goods.getId(), 5, 0, 2, 3);
        assertThat(rightMapper.selectById(fixture.right.getId()))
                .extracting(UmsMemberQuantityRight::getRemainingQuantity, UmsMemberQuantityRight::getPickedQuantity)
                .containsExactly(5, 0);
        assertThat(goodsMapper.selectById(fixture.goods.getId()).getStock()).isEqualTo(8L);
        assertThat(pickupMapper.selectCount(new LambdaQueryWrapper<OmsMemberQuantityPickup>()
                .eq(OmsMemberQuantityPickup::getMemberId, fixture.member.getId()))).isZero();
    }

    @Test
    void insufficientPhysicalStockRollsBackRightAndPickupRecord() {
        Fixture fixture = purchaseRight(2, 1);
        DeferredQuantityPickupDTO request = pickupRequest("PICKUP-NOSTOCK-" + fixture.suffix, fixture.member.getId(), fixture.right.getId(), 2);

        assertThatThrownBy(() -> pickupService.pickup(request)).isInstanceOf(BaseException.class);

        assertThat(goodsMapper.selectById(fixture.goods.getId()).getStock()).isEqualTo(1L);
    }

    @Test
    void insufficientQuantityRightRejectsPickupWithoutStockMutation() {
        Fixture fixture = purchaseRight(2, 5);
        DeferredQuantityPickupDTO request = pickupRequest("PICKUP-NORIGHT-" + fixture.suffix,
                fixture.member.getId(), fixture.right.getId(), 3);

        assertThatThrownBy(() -> pickupService.pickup(request)).isInstanceOf(BaseException.class);

        assertThat(goodsMapper.selectById(fixture.goods.getId()).getStock()).isEqualTo(5L);
        assertThat(rightMapper.selectById(fixture.right.getId()).getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void unpickedQuantityRefundCancelsRightWithoutRestocking() {
        Fixture fixture = purchaseRight(2, 5);
        String refundNo = "REFUND-UNPICKED-" + fixture.suffix;

        refundService.returnOrder(refundNo, "BUY-" + fixture.suffix);

        assertThat(goodsMapper.selectById(fixture.goods.getId()).getStock()).isEqualTo(5L);
        assertThat(rightMapper.selectById(fixture.right.getId()).getRemainingQuantity()).isZero();
        assertThat(inventoryDocMapper.selectCount(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocNo, "MPR-" + refundNo))).isZero();
        assertThatThrownBy(() -> refundService.returnOrder(refundNo, "BUY-" + fixture.suffix))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void pickedQuantityRefundUsesMemberPickupReturnInsteadOfNormalReturn() {
        Fixture fixture = purchaseRight(2, 5);
        DeferredQuantityPickupDTO pickup = pickupRequest("PICKUP-RETURN-" + fixture.suffix,
                fixture.member.getId(), fixture.right.getId(), 2);
        pickupService.pickup(pickup);
        String refundNo = "REFUND-PICKED-" + fixture.suffix;

        refundService.returnOrder(refundNo, "BUY-" + fixture.suffix);

        assertThat(goodsMapper.selectById(fixture.goods.getId()).getStock()).isEqualTo(5L);
        assertThat(inventoryDocMapper.selectOne(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocNo, "MPR-" + refundNo)))
                .extracting(GmsInventoryDoc::getDocType).isEqualTo("MEMBER_PICKUP_RETURN");
    }

    @Test
    void comboPickupReturnRestoresBothAllocationAndPhysicalStock() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoods component = tradeFixture.createSellableGoods(suffix + "c", 5, new BigDecimal("12.00"));
        GmsGoods combo = tradeFixture.createSellableGoods(suffix + "b", 3, new BigDecimal("12.00"));
        combo.setBrandId(1L); combo.setIsCombo(1); goodsMapper.updateById(combo);
        GmsGoodsCombo composition = new GmsGoodsCombo();
        composition.setComboGoodsId(combo.getId()); composition.setSubGoodsId(component.getId()); composition.setSubGoodsQty(2);
        goodsComboMapper.insert(composition);
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        com.money.dto.pos.SettleAccountsDTO purchase = tradeFixture.cashSettlement("BUY-COMBO-" + suffix, combo.getId(), 1, new BigDecimal("12.00"));
        purchase.setMember(member.getId()); checkoutOrchestrator.orchestrateDeferredQuantity(purchase);
        UmsMemberQuantityRight right = rightMapper.selectOne(new LambdaQueryWrapper<UmsMemberQuantityRight>()
                .eq(UmsMemberQuantityRight::getSourceOrderNo, "BUY-COMBO-" + suffix));
        pickupService.pickup(pickupRequest("PICKUP-COMBO-" + suffix, member.getId(), right.getId(), 1));

        refundService.returnOrder("REFUND-COMBO-" + suffix, "BUY-COMBO-" + suffix);

        assertThat(goodsMapper.selectById(combo.getId()).getStock()).isEqualTo(3L);
        assertThat(goodsMapper.selectById(component.getId()).getStock()).isEqualTo(5L);
    }

    private Fixture purchaseRight(int quantity, long stock) {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, stock, new BigDecimal("12.00"));
        goods.setBrandId(1L);
        goodsMapper.updateById(goods);
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        com.money.dto.pos.SettleAccountsDTO purchase = tradeFixture.cashSettlement("BUY-" + suffix, goods.getId(), quantity,
                new BigDecimal("12.00").multiply(new BigDecimal(quantity)));
        purchase.setMember(member.getId());
        checkoutOrchestrator.orchestrateDeferredQuantity(purchase);
        UmsMemberQuantityRight right = rightMapper.selectOne(new LambdaQueryWrapper<UmsMemberQuantityRight>()
                .eq(UmsMemberQuantityRight::getSourceOrderNo, "BUY-" + suffix));
        return new Fixture(suffix, goods, member, right);
    }

    private DeferredQuantityPickupDTO pickupRequest(String requestNo, Long memberId, Long rightId, int quantity) {
        DeferredQuantityPickupDTO.Line line = new DeferredQuantityPickupDTO.Line();
        line.setRightId(rightId);
        line.setQuantity(quantity);
        DeferredQuantityPickupDTO request = new DeferredQuantityPickupDTO();
        request.setReqId(requestNo);
        request.setMemberId(memberId);
        request.setLines(Collections.singletonList(line));
        return request;
    }

    private DeferredQuantityPickupPreviewDTO previewRequest(Long memberId, Long rightId, int quantity) {
        DeferredQuantityPickupPreviewDTO.Line line = new DeferredQuantityPickupPreviewDTO.Line();
        line.setRightId(rightId); line.setQuantity(quantity);
        DeferredQuantityPickupPreviewDTO request = new DeferredQuantityPickupPreviewDTO();
        request.setMemberId(memberId); request.setLines(Collections.singletonList(line));
        return request;
    }

    private static final class Fixture {
        private final String suffix;
        private final GmsGoods goods;
        private final UmsMember member;
        private final UmsMemberQuantityRight right;

        private Fixture(String suffix, GmsGoods goods, UmsMember member, UmsMemberQuantityRight right) {
            this.suffix = suffix;
            this.goods = goods;
            this.member = member;
            this.right = right;
        }
    }
}
