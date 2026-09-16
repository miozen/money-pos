package com.money.feature.trade.application.checkout;

import com.money.dto.pos.SettleResultVO;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.entity.GmsGoods;
import com.money.entity.OmsOrder;
import com.money.entity.OmsOrderDetail;
import com.money.entity.UmsMember;
import com.money.entity.PosCouponRule;
import com.money.entity.PosMemberCoupon;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.support.TradeFixture;
import com.money.feature.trade.application.refund.OmsOrderRefundService;
import com.money.web.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class CheckoutIntegrationTest {

    @Autowired
    private CheckoutOrchestrator checkoutOrchestrator;
    
    @Autowired
    private OmsOrderRefundService refundService;
    @Autowired
    private TradeFixture tradeFixture;
    @Autowired
    private GmsGoodsMapper gmsGoodsMapper;
    @Autowired
    private UmsMemberMapper umsMemberMapper;
    @Autowired
    private PosMemberCouponMapper posMemberCouponMapper;
    @Autowired
    private OmsOrderMapper omsOrderMapper;
    
    @Autowired
    private OmsOrderDetailMapper omsOrderDetailMapper;
    @Autowired
    private OmsOrderPayMapper omsOrderPayMapper;
    @Autowired
    private GmsInventoryDocMapper inventoryDocMapper;

    @BeforeEach
    void authenticateFixtureWriter() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void cashSettlementPersistsOrderPaymentStockAndSaleOutDocument() {
        String suffix = String.valueOf(System.nanoTime());
        String requestId = "SETTLE-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));

        SettleResultVO result = checkoutOrchestrator.orchestrate(
                tradeFixture.cashSettlement(requestId, goods.getId(), 2, new BigDecimal("24.00")));

        OmsOrder order = omsOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                        .eq(OmsOrder::getOrderNo, requestId));
        GmsGoods updatedGoods = gmsGoodsMapper.selectById(goods.getId());

        assertThat(result.getOrderNo()).isEqualTo(requestId);
        assertThat(order).isNotNull();
        assertThat(order.getPayAmount()).isEqualByComparingTo("24.00");
        assertThat(updatedGoods.getStock()).isEqualTo(8L);
        assertThat(omsOrderPayMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.money.entity.OmsOrderPay>()
                        .eq(com.money.entity.OmsOrderPay::getOrderNo, requestId))).isEqualTo(1);
        assertThat(inventoryDocMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.money.entity.GmsInventoryDoc>()
                        .eq(com.money.entity.GmsInventoryDoc::getDocNo, "XS-" + requestId))).isEqualTo(1);
    }


    
    @Test
    void fullRefundRestoresStockAndMarksOrderAndDetailRefunded() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "REFUND-FULL-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        checkoutOrchestrator.orchestrate(tradeFixture.cashSettlement(orderNo, goods.getId(), 2, new BigDecimal("24.00")));

        refundService.returnOrder("FULL-" + suffix, orderNo);

        OmsOrder order = omsOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo));
        OmsOrderDetail detail = omsOrderDetailMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                .eq(OmsOrderDetail::getOrderNo, orderNo));
        assertThat(order.getStatus()).isEqualTo("REFUNDED");
        assertThat(detail.getReturnQuantity()).isEqualTo(2);
        assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(10L);
    }

    
    @Test
    void partialRefundRestoresOnlyReturnedQuantityAndMarksOrderPartialRefunded() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "REFUND-PARTIAL-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        checkoutOrchestrator.orchestrate(tradeFixture.cashSettlement(orderNo, goods.getId(), 3, new BigDecimal("36.00")));
        OmsOrderDetail detail = omsOrderDetailMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                .eq(OmsOrderDetail::getOrderNo, orderNo));
        ReturnGoodsDTO request = new ReturnGoodsDTO();
        request.setReqId("PARTIAL-" + suffix);
        request.setOrderNo(orderNo);
        request.setDetailId(detail.getId());
        request.setReturnQty(1);

        refundService.returnGoods(request);

        OmsOrder order = omsOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo));
        OmsOrderDetail updatedDetail = omsOrderDetailMapper.selectById(detail.getId());
        assertThat(order.getStatus()).isEqualTo("PARTIAL_REFUNDED");
        assertThat(updatedDetail.getReturnQuantity()).isEqualTo(1);
        assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(8L);
    }

    
    @Test
    void memberBalancePaymentConsumesBalanceAndRecordsMemberConsumption() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "BALANCE-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("50.00"));

        checkoutOrchestrator.orchestrate(tradeFixture.balanceSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("24.00")));

        UmsMember updatedMember = umsMemberMapper.selectById(member.getId());
        assertThat(updatedMember.getBalance()).isEqualByComparingTo("26.00");
        assertThat(updatedMember.getConsumeAmount()).isEqualByComparingTo("24.00");
        assertThat(updatedMember.getConsumeTimes()).isEqualTo(1);
        assertThat(omsOrderPayMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.money.entity.OmsOrderPay>()
                .eq(com.money.entity.OmsOrderPay::getOrderNo, orderNo)
                .eq(com.money.entity.OmsOrderPay::getPayMethodCode, "BALANCE"))).isEqualTo(1);
        OmsOrder order = omsOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo));
        assertThat(order.getMemberId()).isEqualTo(member.getId());
        assertThat(order.getMember()).isEqualTo(member.getName());
        assertThat(order.getContact()).isEqualTo(member.getPhone());
    }

    @Test
    void memberBalanceFullRefundRestoresBalanceAndConsumption() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "RB-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("50.00"));
        checkoutOrchestrator.orchestrate(tradeFixture.balanceSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("24.00")));

        refundService.returnOrder("RBF-" + suffix, orderNo);

        UmsMember updatedMember = umsMemberMapper.selectById(member.getId());
        assertThat(updatedMember.getBalance()).isEqualByComparingTo("50.00");
        assertThat(updatedMember.getConsumeAmount()).isEqualByComparingTo("0.00");
        assertThat(updatedMember.getCancelTimes()).isEqualTo(1);
    }

    @Test
    void couponSettlementDeductsVoucherAndMarksMemberCouponUsed() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "CP-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));
        PosMemberCoupon coupon = tradeFixture.issueCoupon(member.getId(), rule.getId());

        checkoutOrchestrator.orchestrate(tradeFixture.couponSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("19.00"), rule.getId()));

        OmsOrder order = omsOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo));
        PosMemberCoupon updatedCoupon = posMemberCouponMapper.selectById(coupon.getId());
        assertThat(order.getUseVoucherAmount()).isEqualByComparingTo("5.00");
        assertThat(order.getPayAmount()).isEqualByComparingTo("19.00");
        assertThat(updatedCoupon.getStatus()).isEqualTo("USED");
        assertThat(updatedCoupon.getOrderNo()).isEqualTo(orderNo);
    }

    @Test
    void couponFullRefundRestoresMemberCoupon() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "CR-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));
        PosMemberCoupon coupon = tradeFixture.issueCoupon(member.getId(), rule.getId());
        checkoutOrchestrator.orchestrate(tradeFixture.couponSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("19.00"), rule.getId()));

        refundService.returnOrder("CRF-" + suffix, orderNo);

        PosMemberCoupon updatedCoupon = posMemberCouponMapper.selectById(coupon.getId());
        assertThat(updatedCoupon.getStatus()).isEqualTo("UNUSED");
        assertThat(updatedCoupon.getOrderNo()).isNull();
    }

    @Test
    void mixedCashAndBalanceSettlementPersistsBothPaymentsAndDeductsOnlyBalance() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "MX-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("20.00"));

        checkoutOrchestrator.orchestrate(tradeFixture.mixedCashBalanceSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("10.00"), new BigDecimal("14.00")));

        assertThat(umsMemberMapper.selectById(member.getId()).getBalance()).isEqualByComparingTo("6.00");
        assertThat(omsOrderPayMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.money.entity.OmsOrderPay>()
                .eq(com.money.entity.OmsOrderPay::getOrderNo, orderNo))).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insufficientBalanceRollsBackOrderStockAndMemberAssetWrites() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "BR-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("10.00"));

        try {
            assertThatThrownBy(() -> checkoutOrchestrator.orchestrate(
                    tradeFixture.balanceSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("24.00"))))
                    .isInstanceOf(BaseException.class);

            UmsMember updatedMember = umsMemberMapper.selectById(member.getId());
            assertThat(omsOrderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo))).isZero();
            assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(10L);
            assertThat(updatedMember.getBalance()).isEqualByComparingTo("10.00");
            assertThat(updatedMember.getConsumeAmount()).isEqualByComparingTo("0.00");
            assertThat(updatedMember.getConsumeTimes()).isZero();
        } finally {
            omsOrderPayMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.money.entity.OmsOrderPay>()
                    .eq(com.money.entity.OmsOrderPay::getOrderNo, orderNo));
            omsOrderDetailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                    .eq(OmsOrderDetail::getOrderNo, orderNo));
            omsOrderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo));
            inventoryDocMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.money.entity.GmsInventoryDoc>()
                    .eq(com.money.entity.GmsInventoryDoc::getDocNo, "XS-" + orderNo));
            umsMemberMapper.deleteById(member.getId());
            gmsGoodsMapper.deleteById(goods.getId());
        }
    }

    @Test
    void insufficientPaymentRollsBackOrderAndStock() {
        String suffix = String.valueOf(System.nanoTime());
        String requestId = "ROLLBACK-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));

        assertThatThrownBy(() -> checkoutOrchestrator.orchestrate(
                tradeFixture.cashSettlement(requestId, goods.getId(), 2, new BigDecimal("10.00"))))
                .isInstanceOf(BaseException.class);

        assertThat(omsOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                        .eq(OmsOrder::getOrderNo, requestId))).isZero();
        assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(10L);
    }
}
