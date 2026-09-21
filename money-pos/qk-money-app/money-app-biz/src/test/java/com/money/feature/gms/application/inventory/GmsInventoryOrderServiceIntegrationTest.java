package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.dto.inventory.GmsInventoryOrderDTO;
import com.money.dto.inventory.GmsInventoryOrderDetailDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryOrder;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryOrderDetail;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsInventoryOrderDetailMapper;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsInventoryOrderMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsStockLogMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsInventoryOrderServiceIntegrationTest {

    @Autowired
    private GmsInventoryOrderService inventoryOrderService;
    @Autowired
    private GmsInventoryOrderMapper inventoryOrderMapper;
    @Autowired
    private GmsInventoryOrderDetailMapper inventoryOrderDetailMapper;
    @Autowired
    private GmsGoodsMapper goodsMapper;
    @Autowired
    private GmsStockLogMapper stockLogMapper;
    @Autowired
    private TradeFixture tradeFixture;

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
    void inboundOrderPersistsCompletedMainDetailStockLogAndWeightedCost() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));

        inventoryOrderService.createInboundOrder(command("inbound-" + suffix, goods.getId(), 2, new BigDecimal("6.00")));

        GmsInventoryOrder order = findOrder("inbound-" + suffix);
        assertThat(order).extracting(GmsInventoryOrder::getType, GmsInventoryOrder::getStatus,
                        GmsInventoryOrder::getTotalAmount)
                .containsExactly("INBOUND", "COMPLETED", new BigDecimal("12.00"));
        assertThat(inventoryOrderDetailMapper.selectList(new LambdaQueryWrapper<GmsInventoryOrderDetail>()
                .eq(GmsInventoryOrderDetail::getOrderId, order.getId())))
                .singleElement()
                .extracting(GmsInventoryOrderDetail::getGoodsId, GmsInventoryOrderDetail::getQty,
                        GmsInventoryOrderDetail::getPrice)
                .containsExactly(goods.getId(), 2, new BigDecimal("6.00"));
        assertThat(stockLogMapper.selectList(new LambdaQueryWrapper<GmsStockLog>()
                .eq(GmsStockLog::getOrderNo, order.getOrderNo())))
                .singleElement()
                .extracting(GmsStockLog::getType, GmsStockLog::getQuantity, GmsStockLog::getAfterQuantity,
                        GmsStockLog::getCostPriceSnapshot, GmsStockLog::getImpactAmount)
                .containsExactly("INBOUND", 2, 12, new BigDecimal("6.00"), new BigDecimal("12.00"));
        assertThat(goodsMapper.selectById(goods.getId()))
                .extracting(GmsGoods::getStock, GmsGoods::getAvgCostPrice, GmsGoods::getPurchasePrice,
                        GmsGoods::getLastPurchasePrice)
                .containsExactly(12L, new BigDecimal("4.33"), new BigDecimal("4.33"), new BigDecimal("6.00"));
    }

    @Test
    void checkOrderPersistsCompletedMainDetailStockLogAndDifference() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));

        inventoryOrderService.createCheckOrder(command("check-" + suffix, goods.getId(), 7, BigDecimal.ZERO));

        GmsInventoryOrder order = findOrder("check-" + suffix);
        assertThat(order).extracting(GmsInventoryOrder::getType, GmsInventoryOrder::getStatus,
                        GmsInventoryOrder::getTotalAmount)
                .containsExactly("CHECK", "COMPLETED", new BigDecimal("0.00"));
        assertThat(inventoryOrderDetailMapper.selectList(new LambdaQueryWrapper<GmsInventoryOrderDetail>()
                .eq(GmsInventoryOrderDetail::getOrderId, order.getId())))
                .singleElement()
                .extracting(GmsInventoryOrderDetail::getGoodsId, GmsInventoryOrderDetail::getQty,
                        GmsInventoryOrderDetail::getPrice)
                .containsExactly(goods.getId(), 7, new BigDecimal("0.00"));
        assertThat(stockLogMapper.selectList(new LambdaQueryWrapper<GmsStockLog>()
                .eq(GmsStockLog::getOrderNo, order.getOrderNo())))
                .singleElement()
                .extracting(GmsStockLog::getType, GmsStockLog::getQuantity, GmsStockLog::getAfterQuantity,
                        GmsStockLog::getCostPriceSnapshot, GmsStockLog::getImpactAmount)
                .containsExactly("CHECK", -3, 7, new BigDecimal("4.00"), new BigDecimal("-12.00"));
        assertThat(goodsMapper.selectById(goods.getId()).getStock()).isEqualTo(7L);
    }

    @Test
    void outboundOrderPersistsCompletedMainDetailStockLogAndScrapDeduction() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 10L, new BigDecimal("12.00"));

        inventoryOrderService.createOutboundOrder(command("outbound-" + suffix, goods.getId(), 3, new BigDecimal("6.00")));

        GmsInventoryOrder order = findOrder("outbound-" + suffix);
        assertThat(order).extracting(GmsInventoryOrder::getType, GmsInventoryOrder::getStatus,
                        GmsInventoryOrder::getTotalAmount)
                .containsExactly("OUTBOUND", "COMPLETED", new BigDecimal("18.00"));
        assertThat(inventoryOrderDetailMapper.selectList(new LambdaQueryWrapper<GmsInventoryOrderDetail>()
                .eq(GmsInventoryOrderDetail::getOrderId, order.getId())))
                .singleElement()
                .extracting(GmsInventoryOrderDetail::getGoodsId, GmsInventoryOrderDetail::getQty,
                        GmsInventoryOrderDetail::getPrice)
                .containsExactly(goods.getId(), 3, new BigDecimal("6.00"));
        assertThat(stockLogMapper.selectList(new LambdaQueryWrapper<GmsStockLog>()
                .eq(GmsStockLog::getOrderNo, order.getOrderNo())))
                .singleElement()
                .extracting(GmsStockLog::getType, GmsStockLog::getQuantity, GmsStockLog::getAfterQuantity,
                        GmsStockLog::getCostPriceSnapshot, GmsStockLog::getImpactAmount)
                .containsExactly("SCRAP", -3, 7, new BigDecimal("4.00"), new BigDecimal("-12.00"));
        assertThat(goodsMapper.selectById(goods.getId()).getStock()).isEqualTo(7L);
    }

    private GmsInventoryOrder findOrder(String remark) {
        return inventoryOrderMapper.selectOne(new LambdaQueryWrapper<GmsInventoryOrder>()
                .eq(GmsInventoryOrder::getRemark, remark));
    }

    private GmsInventoryOrderDTO command(String remark, Long goodsId, int quantity, BigDecimal price) {
        GmsInventoryOrderDetailDTO detail = new GmsInventoryOrderDetailDTO();
        detail.setGoodsId(goodsId);
        detail.setQty(quantity);
        detail.setPrice(price);
        GmsInventoryOrderDTO command = new GmsInventoryOrderDTO();
        command.setRemark(remark);
        command.setDetails(Collections.singletonList(detail));
        return command;
    }
}
