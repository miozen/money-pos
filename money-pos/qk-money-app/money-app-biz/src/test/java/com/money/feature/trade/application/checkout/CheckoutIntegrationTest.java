package com.money.feature.trade.application.checkout;

import com.money.dto.pos.SettleResultVO;
import com.money.dto.OmsOrder.ReturnGoodsDTO;
import com.money.constant.BizErrorStatus;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrder;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderDetail;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.feature.ums.infrastructure.persistence.entity.PosMemberCoupon;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderPay;
import com.money.feature.trade.infrastructure.persistence.entity.OmsOrderLog;
import com.money.feature.trade.infrastructure.persistence.mapper.OmsOrderLogMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.mapper.OmsOrderMapper;
import com.money.mapper.OmsOrderDetailMapper;
import com.money.mapper.OmsOrderPayMapper;
import com.money.mapper.UmsMemberMapper;
import com.money.mapper.PosMemberCouponMapper;
import com.money.support.TradeFixture;
import com.money.feature.trade.application.refund.OmsOrderRefundService;
import com.money.feature.trade.application.orderquery.OmsOrderService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    private OmsOrderService omsOrderService;
    @Autowired
    private TradeFixture tradeFixture;
    @Autowired
    private GmsGoodsMapper gmsGoodsMapper;
    @Autowired
    private GmsGoodsComboMapper gmsGoodsComboMapper;
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
    private OmsOrderLogMapper omsOrderLogMapper;
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
        assertThat(omsOrderPayMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderPay>()
                        .eq(OmsOrderPay::getOrderNo, requestId)))
                .singleElement()
                .extracting(OmsOrderPay::getOrderNo,
                        OmsOrderPay::getPayMethodCode,
                        OmsOrderPay::getPayMethodName,
                        OmsOrderPay::getPayAmount,
                        OmsOrderPay::getOriginalAmount,
                        OmsOrderPay::getNetAmount,
                        OmsOrderPay::getChangeAllocated)
                .containsExactly(requestId, "CASH", "现金支付", new BigDecimal("24.00"), new BigDecimal("24.00"),
                        new BigDecimal("24.00"), new BigDecimal("0.00"));
        assertThat(omsOrderLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderLog>()
                        .eq(OmsOrderLog::getOrderId, order.getId())))
                .singleElement()
                .extracting(OmsOrderLog::getDescription)
                .asString()
                .contains("\"action\":\"SETTLE_SUCCESS\"", "\"orderNo\":\"" + requestId + "\"");
        assertThat(omsOrderService.getOrderDetailByNo(requestId).getOrderLog())
                .singleElement()
                .extracting(com.money.dto.OmsOrder.OrderDetailVO.OrderLogVO::getDescription)
                .asString()
                .contains("\"action\":\"SETTLE_SUCCESS\"");
        assertThat(inventoryDocMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GmsInventoryDoc>()
                        .eq(GmsInventoryDoc::getDocNo, "XS-" + requestId))).isEqualTo(1);
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
        List<OmsOrderLog> fullRefundLogs = omsOrderLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderLog>()
                        .eq(OmsOrderLog::getOrderId, order.getId())
                        .orderByAsc(OmsOrderLog::getCreateTime));
        assertThat(fullRefundLogs)
                .extracting(OmsOrderLog::getDescription)
                .anySatisfy(description -> assertThat(description).contains("\"action\":\"SETTLE_SUCCESS\""))
                .anySatisfy(description -> assertThat(description).isEqualTo("执行整单退款操作，资产与满减券已原路回退"));
        assertThat(fullRefundLogs).extracting(OmsOrderLog::getCreateTime).isSorted();
        assertThat(omsOrderService.getOrderDetailByNo(orderNo).getOrderLog())
                .extracting(com.money.dto.OmsOrder.OrderDetailVO.OrderLogVO::getId)
                .containsExactly(fullRefundLogs.get(0).getId(), fullRefundLogs.get(1).getId());
    }

    @Test
    void duplicateFullRefundRequestIsRejectedByTheTradeIdempotentRecord() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "RI-" + suffix;
        String refundRequestId = "RR-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        checkoutOrchestrator.orchestrate(tradeFixture.cashSettlement(orderNo, goods.getId(), 2, new BigDecimal("24.00")));

        refundService.returnOrder(refundRequestId, orderNo);

        assertThatThrownBy(() -> refundService.returnOrder(refundRequestId, orderNo))
                .isInstanceOf(BaseException.class)
                .satisfies(error -> assertThat(((BaseException) error).getErrorCode())
                        .isEqualTo(BizErrorStatus.POS_REFUND_REPEAT.getCode()));
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
        List<OmsOrderLog> partialRefundLogs = omsOrderLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderLog>()
                        .eq(OmsOrderLog::getOrderId, order.getId())
                        .orderByAsc(OmsOrderLog::getCreateTime));
        assertThat(partialRefundLogs)
                .extracting(OmsOrderLog::getDescription)
                .anySatisfy(description -> assertThat(description).contains("\"action\":\"SETTLE_SUCCESS\""))
                .anySatisfy(description -> assertThat(description).contains("执行部分退货:", "x1"));
        assertThat(partialRefundLogs).extracting(OmsOrderLog::getCreateTime).isSorted();
    }

    @Test
    void comboCheckoutAndFullRefundPropagatePhysicalStockThroughGmsCommand() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String orderNo = "COMBO-" + suffix;
        GmsGoods component = tradeFixture.createSellableGoods(suffix + "c", 10L, new BigDecimal("12.00"));
        GmsGoods combo = tradeFixture.createSellableGoods(suffix + "b", 5L, new BigDecimal("12.00"));
        combo.setIsCombo(1);
        gmsGoodsMapper.updateById(combo);
        GmsGoodsCombo composition = new GmsGoodsCombo();
        composition.setComboGoodsId(combo.getId());
        composition.setSubGoodsId(component.getId());
        composition.setSubGoodsQty(2);
        gmsGoodsComboMapper.insert(composition);

        checkoutOrchestrator.orchestrate(tradeFixture.cashSettlement(orderNo, combo.getId(), 1, new BigDecimal("12.00")));

        assertThat(gmsGoodsMapper.selectById(combo.getId()).getStock()).isEqualTo(4L);
        assertThat(gmsGoodsMapper.selectById(component.getId()).getStock()).isEqualTo(8L);
        refundService.returnOrder("COMBO-REFUND-" + suffix, orderNo);
        assertThat(gmsGoodsMapper.selectById(combo.getId()).getStock()).isEqualTo(5L);
        assertThat(gmsGoodsMapper.selectById(component.getId()).getStock()).isEqualTo(10L);
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
        assertThat(omsOrderPayMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderPay>()
                .eq(OmsOrderPay::getOrderNo, orderNo)
                .eq(OmsOrderPay::getPayMethodCode, "BALANCE"))).isEqualTo(1);
        OmsOrder order = omsOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo));
        assertThat(order.getMemberId()).isEqualTo(member.getId());
        assertThat(order.getMember()).isEqualTo(member.getName());
        assertThat(order.getContact()).isEqualTo(member.getPhone());
        assertThat(omsOrderService.getOrderDetailByNo(orderNo).getMemberInfo().getName()).isEqualTo(member.getName());
        assertThat(omsOrderService.getOrderDetailByNo(orderNo).getMemberInfo().getPhone()).isEqualTo(member.getPhone());
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
    void partialRefundAfterBalancePaymentDoesNotRestoreTheWholeOrderBalance() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "RB-PARTIAL-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("50.00"));
        checkoutOrchestrator.orchestrate(tradeFixture.balanceSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("24.00")));
        OmsOrderDetail detail = omsOrderDetailMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                .eq(OmsOrderDetail::getOrderNo, orderNo));
        ReturnGoodsDTO request = new ReturnGoodsDTO();
        request.setReqId("RB-PARTIAL-RETURN-" + suffix);
        request.setOrderNo(orderNo);
        request.setDetailId(detail.getId());
        request.setReturnQty(1);

        refundService.returnGoods(request);

        UmsMember updatedMember = umsMemberMapper.selectById(member.getId());
        assertThat(updatedMember.getBalance()).isEqualByComparingTo("26.00");
        assertThat(updatedMember.getConsumeAmount()).isEqualByComparingTo("12.00");
        assertThat(omsOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                .eq(OmsOrder::getOrderNo, orderNo)).getStatus()).isEqualTo("PARTIAL_REFUNDED");
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
    void couponSettlementConsumesVouchersInFifoOrder() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "FIFO-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("10.00"), new BigDecimal("5.00"));
        PosMemberCoupon oldest = tradeFixture.issueCoupon(member.getId(), rule.getId());
        PosMemberCoupon middle = tradeFixture.issueCoupon(member.getId(), rule.getId());
        PosMemberCoupon newest = tradeFixture.issueCoupon(member.getId(), rule.getId());
        oldest.setGetTime(LocalDateTime.now().minusMinutes(2));
        middle.setGetTime(LocalDateTime.now().minusMinutes(1));
        newest.setGetTime(LocalDateTime.now());
        posMemberCouponMapper.updateById(oldest);
        posMemberCouponMapper.updateById(middle);
        posMemberCouponMapper.updateById(newest);

        com.money.dto.pos.SettleAccountsDTO request = tradeFixture.cashSettlement(
                orderNo, goods.getId(), 3, new BigDecimal("26.00"));
        request.setMember(member.getId());
        request.setUsedCouponRuleId(rule.getId());
        request.setUsedCouponCount(2);
        checkoutOrchestrator.orchestrate(request);

        assertThat(posMemberCouponMapper.selectById(oldest.getId()).getStatus()).isEqualTo("USED");
        assertThat(posMemberCouponMapper.selectById(middle.getId()).getStatus()).isEqualTo("USED");
        assertThat(posMemberCouponMapper.selectById(newest.getId()).getStatus()).isEqualTo("UNUSED");
    }

    @Test
    void mixedCashAndBalanceSettlementPersistsBothPaymentsAndDeductsOnlyBalance() {
        String suffix = String.valueOf(System.nanoTime());
        String orderNo = "MX-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("20.00"));

        checkoutOrchestrator.orchestrate(tradeFixture.mixedCashBalanceSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("10.00"), new BigDecimal("14.00")));

        assertThat(umsMemberMapper.selectById(member.getId()).getBalance()).isEqualByComparingTo("6.00");
        assertThat(omsOrderPayMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderPay>()
                .eq(OmsOrderPay::getOrderNo, orderNo))).isEqualTo(2);
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
            omsOrderPayMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderPay>()
                    .eq(OmsOrderPay::getOrderNo, orderNo));
            omsOrderDetailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                    .eq(OmsOrderDetail::getOrderNo, orderNo));
            omsOrderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo));
            inventoryDocMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GmsInventoryDoc>()
                    .eq(GmsInventoryDoc::getDocNo, "XS-" + orderNo));
            umsMemberMapper.deleteById(member.getId());
            gmsGoodsMapper.deleteById(goods.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insufficientVoucherCountRollsBackOrderStockAndMemberAssets() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String orderNo = "VR-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("10.00"), new BigDecimal("1.00"));
        PosMemberCoupon coupon = tradeFixture.issueCoupon(member.getId(), rule.getId());

        try {
            com.money.dto.pos.SettleAccountsDTO request = tradeFixture.cashSettlement(orderNo, goods.getId(), 2, new BigDecimal("22.00"));
            request.setMember(member.getId());
            request.setUsedCouponRuleId(rule.getId());
            request.setUsedCouponCount(2);

            assertThatThrownBy(() -> checkoutOrchestrator.orchestrate(request)).isInstanceOf(BaseException.class);

            assertThat(omsOrderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo))).isZero();
            assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(10L);
            assertThat(umsMemberMapper.selectById(member.getId()).getConsumeAmount()).isEqualByComparingTo("0.00");
            assertThat(posMemberCouponMapper.selectById(coupon.getId()).getStatus()).isEqualTo("UNUSED");
        } finally {
            posMemberCouponMapper.deleteById(coupon.getId());
            omsOrderPayMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderPay>()
                    .eq(OmsOrderPay::getOrderNo, orderNo));
            omsOrderDetailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                    .eq(OmsOrderDetail::getOrderNo, orderNo));
            omsOrderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo));
            inventoryDocMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GmsInventoryDoc>()
                    .eq(GmsInventoryDoc::getDocNo, "XS-" + orderNo));
            umsMemberMapper.deleteById(member.getId());
            gmsGoodsMapper.deleteById(goods.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCouponSettlementsAllowOnlyOneOrderToConsumeTheSameVoucher() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String firstOrderNo = "VC-A-" + suffix;
        String secondOrderNo = "VC-B-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));
        PosMemberCoupon voucher = tradeFixture.issueCoupon(member.getId(), rule.getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> settleCouponWhenReleased(
                    ready, start, tradeFixture.couponSettlement(
                            firstOrderNo, member.getId(), goods.getId(), 2, new BigDecimal("19.00"), rule.getId())));
            Future<Boolean> second = executor.submit(() -> settleCouponWhenReleased(
                    ready, start, tradeFixture.couponSettlement(
                            secondOrderNo, member.getId(), goods.getId(), 2, new BigDecimal("19.00"), rule.getId())));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long successfulSettlements = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successfulSettlements).isEqualTo(1);
            assertThat(omsOrderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .in(OmsOrder::getOrderNo, firstOrderNo, secondOrderNo))).isEqualTo(1);
            assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(8L);
            assertThat(umsMemberMapper.selectById(member.getId()).getConsumeAmount()).isEqualByComparingTo("19.00");
            PosMemberCoupon updatedVoucher = posMemberCouponMapper.selectById(voucher.getId());
            assertThat(updatedVoucher.getStatus()).isEqualTo("USED");
            assertThat(updatedVoucher.getOrderNo()).isIn(firstOrderNo, secondOrderNo);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            posMemberCouponMapper.deleteById(voucher.getId());
            deleteCheckoutArtifacts(firstOrderNo, secondOrderNo);
            umsMemberMapper.deleteById(member.getId());
            gmsGoodsMapper.deleteById(goods.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void repeatedCouponSettlementRequestIsIdempotentAndDoesNotConsumeAssetsTwice() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String orderNo = "IDM-" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));
        UmsMember member = tradeFixture.createMember(suffix, BigDecimal.ZERO);
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));
        PosMemberCoupon voucher = tradeFixture.issueCoupon(member.getId(), rule.getId());

        try {
            SettleResultVO first = checkoutOrchestrator.orchestrate(
                    tradeFixture.couponSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("19.00"), rule.getId()));
            SettleResultVO retry = checkoutOrchestrator.orchestrate(
                    tradeFixture.couponSettlement(orderNo, member.getId(), goods.getId(), 2, new BigDecimal("19.00"), rule.getId()));

            assertThat(retry.getOrderNo()).isEqualTo(first.getOrderNo());
            assertThat(omsOrderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo))).isEqualTo(1);
            assertThat(gmsGoodsMapper.selectById(goods.getId()).getStock()).isEqualTo(8L);
            assertThat(umsMemberMapper.selectById(member.getId()).getConsumeAmount()).isEqualByComparingTo("19.00");
            assertThat(posMemberCouponMapper.selectById(voucher.getId()).getStatus()).isEqualTo("USED");
        } finally {
            posMemberCouponMapper.deleteById(voucher.getId());
            deleteCheckoutArtifacts(orderNo);
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

    private boolean settleCouponWhenReleased(
            CountDownLatch ready, CountDownLatch start, com.money.dto.pos.SettleAccountsDTO request) throws InterruptedException {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发结算测试未能同时启动");
            }
            checkoutOrchestrator.orchestrate(request);
            return true;
        } catch (BaseException expectedVoucherConflict) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private void deleteCheckoutArtifacts(String... orderNos) {
        for (String orderNo : orderNos) {
            omsOrderPayMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderPay>()
                    .eq(OmsOrderPay::getOrderNo, orderNo));
            omsOrderDetailMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrderDetail>()
                    .eq(OmsOrderDetail::getOrderNo, orderNo));
            omsOrderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OmsOrder>()
                    .eq(OmsOrder::getOrderNo, orderNo));
            inventoryDocMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GmsInventoryDoc>()
                    .eq(GmsInventoryDoc::getDocNo, "XS-" + orderNo));
        }
    }
}
