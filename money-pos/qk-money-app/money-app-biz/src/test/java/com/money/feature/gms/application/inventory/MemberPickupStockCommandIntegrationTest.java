package com.money.feature.gms.application.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.MemberPickupStockCommand;
import com.money.contract.goods.MemberPickupStockCommandHandler;
import com.money.contract.goods.StockMutationLine;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.GmsStockLogMapper;
import com.money.support.TradeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MemberPickupStockCommandIntegrationTest {
    @Autowired private MemberPickupStockCommandHandler memberPickupStockCommandHandler;
    @Autowired private TradeFixture tradeFixture;
    @Autowired private GmsGoodsMapper goodsMapper;
    @Autowired private GmsInventoryDocMapper inventoryDocMapper;
    @Autowired private GmsStockLogMapper stockLogMapper;

    @BeforeEach
    void authenticateTenant() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A"));
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pickupDeductsStockWithoutSaleSemantics() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String pickupNo = "MP" + suffix;
        GmsGoods goods = tradeFixture.createSellableGoods(suffix, 5L, new BigDecimal("12.00"));
        StockMutationLine line = new StockMutationLine();
        line.setGoodsId(goods.getId()); line.setGoodsName(goods.getName()); line.setGoodsBarcode(goods.getBarcode());
        line.setQuantity(2); line.setPurchasePrice(goods.getPurchasePrice()); line.setCombo(false);
        MemberPickupStockCommand command = new MemberPickupStockCommand();
        command.setPickupNo(pickupNo); command.setLines(Collections.singletonList(line));

        memberPickupStockCommandHandler.handle(command);

        assertThat(goodsMapper.selectById(goods.getId()).getStock()).isEqualTo(3L);
        assertThat(inventoryDocMapper.selectOne(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocNo, "MP-" + pickupNo)))
                .extracting(GmsInventoryDoc::getDocType).isEqualTo("MEMBER_PICKUP");
        assertThat(stockLogMapper.selectList(new LambdaQueryWrapper<GmsStockLog>().eq(GmsStockLog::getOrderNo, pickupNo)))
                .extracting(GmsStockLog::getType).containsOnly("MEMBER_PICKUP");
        assertThat(inventoryDocMapper.selectCount(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocType, "SALE_OUT").like(GmsInventoryDoc::getDocNo, pickupNo))).isZero();
    }
}
