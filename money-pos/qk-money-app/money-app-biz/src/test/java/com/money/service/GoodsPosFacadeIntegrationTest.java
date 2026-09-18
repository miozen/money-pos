package com.money.service;

import com.money.contract.goods.LegacyPosGoodsSearchQuery;
import com.money.dto.GmsGoods.GmsGoodsVO;
import com.money.entity.GmsGoods;
import com.money.entity.PosSkuLevelPrice;
import com.money.entity.SysBrandConfig;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
import com.money.mapper.SysBrandConfigMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GoodsPosFacadeIntegrationTest {

    @Autowired
    private GoodsPosFacade goodsPosFacade;
    @Autowired
    private LegacyPosGoodsSearchQuery legacyPosGoodsSearchQuery;
    @Autowired
    private GmsGoodsMapper goodsMapper;
    @Autowired
    private PosSkuLevelPriceMapper levelPriceMapper;
    @Autowired
    private SysBrandConfigMapper brandConfigMapper;

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
    void legacySearchKeepsUngroupedStatusSemanticsRawMnemonicAndEmptyKeyword() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoods barcodeNonSale = createGoods(suffix, "barcode-" + suffix, "other-name-" + suffix,
                "other-mnemonic-" + suffix, "OFF_SHELF", 1001L);
        GmsGoods nameNonSale = createGoods(suffix, "other-barcode-" + suffix, "name-" + suffix,
                "other-mnemonic-name-" + suffix, "OFF_SHELF", 1002L);
        GmsGoods mnemonicNonSale = createGoods(suffix, "other-barcode-mn-" + suffix, "other-name-mn-" + suffix,
                "rawlower-" + suffix, "OFF_SHELF", 1003L);
        GmsGoods mnemonicSale = createGoods(suffix, "other-barcode-sale-" + suffix, "other-name-sale-" + suffix,
                "rawlower-sale-" + suffix, "SALE", 1004L);

        assertThat(goodsPosFacade.posSearchGoods(barcodeNonSale.getBarcode()))
                .extracting(GmsGoodsVO::getId).contains(barcodeNonSale.getId());
        assertThat(goodsPosFacade.posSearchGoods(nameNonSale.getName()))
                .extracting(GmsGoodsVO::getId).contains(nameNonSale.getId());
        assertThat(goodsPosFacade.posSearchGoods(mnemonicNonSale.getMnemonicCode()))
                .extracting(GmsGoodsVO::getId).doesNotContain(mnemonicNonSale.getId());
        assertThat(goodsPosFacade.posSearchGoods(mnemonicSale.getMnemonicCode()))
                .extracting(GmsGoodsVO::getId).contains(mnemonicSale.getId());
        assertThat(goodsPosFacade.posSearchGoods(""))
                .extracting(GmsGoodsVO::getId)
                .contains(barcodeNonSale.getId(), nameNonSale.getId(), mnemonicNonSale.getId(), mnemonicSale.getId());
    }

    @Test
    void legacySearchReturnsGmsPriceMatrixAndFacadeZerosCouponsWhenPolicyIsOff() {
        String suffix = Long.toString(System.nanoTime(), 36);
        Long brandId = 2000L + Math.abs(System.nanoTime() % 100000L);
        GmsGoods goods = createGoods(suffix, "matrix-" + suffix, "matrix-name-" + suffix,
                "matrix-mn-" + suffix, "SALE", brandId);
        addPrice(goods.getId(), "VIP", new BigDecimal("8.50"), new BigDecimal("2.00"));
        SysBrandConfig config = new SysBrandConfig();
        config.setBrand(String.valueOf(brandId));
        config.setCouponEnabled(false);
        config.setTenantId(0L);
        brandConfigMapper.insert(config);

        assertThat(legacyPosGoodsSearchQuery.searchForLegacyPos(goods.getBarcode())).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getLevelPrices()).containsEntry("VIP", new BigDecimal("8.50"));
            assertThat(snapshot.getLevelCoupons()).containsEntry("VIP", new BigDecimal("2.00"));
        });
        assertThat(goodsPosFacade.posSearchGoods(goods.getBarcode())).singleElement().satisfies(vo -> {
            assertThat(vo.getLevelPrices()).containsEntry("VIP", new BigDecimal("8.50"));
            assertThat(vo.getLevelCoupons()).containsEntry("VIP", BigDecimal.ZERO);
        });
    }

    private GmsGoods createGoods(String suffix, String barcode, String name, String mnemonicCode, String status, Long brandId) {
        GmsGoods goods = new GmsGoods();
        goods.setBarcode(barcode);
        goods.setName(name);
        goods.setMnemonicCode(mnemonicCode);
        goods.setBrandId(brandId);
        goods.setCategoryId(1L);
        goods.setPurchasePrice(new BigDecimal("5.00"));
        goods.setSalePrice(new BigDecimal("10.00"));
        goods.setVipPrice(new BigDecimal("9.00"));
        goods.setCoupon(BigDecimal.ZERO);
        goods.setStock(5L);
        goods.setSales(3L);
        goods.setStatus(status);
        goods.setIsDiscountParticipable(1);
        goods.setTenantId(0L);
        goodsMapper.insert(goods);
        return goods;
    }

    private void addPrice(Long goodsId, String levelId, BigDecimal memberPrice, BigDecimal memberCoupon) {
        PosSkuLevelPrice price = new PosSkuLevelPrice();
        price.setSkuId(goodsId);
        price.setLevelId(levelId);
        price.setMemberPrice(memberPrice);
        price.setMemberCoupon(memberCoupon);
        price.setTenantId("0");
        levelPriceMapper.insert(price);
    }
}
