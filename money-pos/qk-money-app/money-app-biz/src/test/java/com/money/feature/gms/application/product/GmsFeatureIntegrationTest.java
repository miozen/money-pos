package com.money.feature.gms.application.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.CheckoutGoodsQuery;
import com.money.contract.goods.CheckoutGoodsSnapshot;
import com.money.contract.goods.PosGoodsCatalogQuery;
import com.money.dto.GmsGoods.GmsGoodsComboDTO;
import com.money.dto.GmsGoods.GmsGoodsDTO;
import com.money.dto.GmsGoods.InventoryDocRequestDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoods;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCategory;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
import com.money.feature.gms.application.catalog.GmsBrandService;
import com.money.feature.gms.application.catalog.GmsGoodsCategoryService;
import com.money.feature.gms.application.inventory.GmsInventoryDocService;
import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDocItem;
import com.money.feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.GmsGoodsComboMapper;
import com.money.mapper.GmsGoodsMapper;
import com.money.mapper.GmsInventoryDocMapper;
import com.money.mapper.GmsInventoryDocItemMapper;
import com.money.mapper.PosSkuLevelPriceMapper;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsFeatureIntegrationTest {

    @Autowired
    private GmsBrandService brandService;
    @Autowired
    private GmsGoodsCategoryService categoryService;
    @Autowired
    private GmsGoodsService goodsService;
    @Autowired
    private GmsGoodsStockService goodsStockService;
    @Autowired
    private GmsInventoryDocService inventoryDocService;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private GmsGoodsCategoryMapper categoryMapper;
    @Autowired
    private GmsGoodsMapper goodsMapper;
    @Autowired
    private GmsGoodsComboMapper comboMapper;
    @Autowired
    private PosSkuLevelPriceMapper levelPriceMapper;
    @Autowired
    private GmsInventoryDocMapper inventoryDocMapper;
    @Autowired
    private GmsInventoryDocItemMapper inventoryDocItemMapper;
    @Autowired
    private CheckoutGoodsQuery checkoutGoodsQuery;
    @Autowired
    private PosGoodsCatalogQuery posGoodsCatalogQuery;

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
    void productCoreSupportsCatalogPriceComboStockAndInboundDocument() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsBrand brand = createBrand(suffix);
        GmsGoodsCategory category = createCategory(suffix);

        assertThat(brandService.getBrandSelect()).anyMatch(item -> item.getValue().equals(brand.getId()));
        assertThat(categoryService.getGoodsCategorySelect()).anyMatch(item -> item.getValue().equals(category.getId()));

        GmsGoods component = createGoods(suffix + "a", brand.getId(), category.getId(), 10L, 0, null);
        GmsGoodsComboDTO componentLine = new GmsGoodsComboDTO();
        componentLine.setSubGoodsId(component.getId());
        componentLine.setSubGoodsQty(2);
        GmsGoods combo = createGoods(suffix + "b", brand.getId(), category.getId(), 5L, 1, List.of(componentLine));

        List<PosSkuLevelPrice> prices = levelPriceMapper.selectList(
                new LambdaQueryWrapper<PosSkuLevelPrice>().eq(PosSkuLevelPrice::getSkuId, component.getId()));
        assertThat(prices).singleElement().satisfies(price -> {
            assertThat(price.getMemberPrice()).isEqualByComparingTo("8.50");
            assertThat(price.getMemberCoupon()).isEqualByComparingTo("1.00");
        });
        CheckoutGoodsSnapshot checkoutSnapshot = checkoutGoodsQuery.findByIds(List.of(component.getId())).get(component.getId());
        assertThat(checkoutSnapshot.getCategoryName()).isEqualTo(category.getName());
        assertThat(checkoutSnapshot.getStock()).isEqualTo(10L);
        assertThat(checkoutSnapshot.getLevelPrices()).containsEntry("VIP", new BigDecimal("8.50"));
        assertThat(checkoutSnapshot.getLevelCoupons()).containsEntry("VIP", new BigDecimal("1.00"));
        assertThat(posGoodsCatalogQuery.searchForPos(component.getBarcode())).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getId()).isEqualTo(component.getId());
            assertThat(snapshot.getLevelPrices()).containsEntry("VIP", new BigDecimal("8.50"));
            assertThat(snapshot.getLevelCoupons()).containsEntry("VIP", new BigDecimal("1.00"));
        });
        assertThat(comboMapper.selectList(new LambdaQueryWrapper<GmsGoodsCombo>()
                .eq(GmsGoodsCombo::getComboGoodsId, combo.getId())))
                .singleElement()
                .extracting(GmsGoodsCombo::getSubGoodsId, GmsGoodsCombo::getSubGoodsQty)
                .containsExactly(component.getId(), 2);

        goodsStockService.updateStock(combo.getId(), -1);
        assertThat(goodsMapper.selectById(combo.getId()).getStock()).isEqualTo(4L);
        assertThat(goodsMapper.selectById(component.getId()).getStock()).isEqualTo(8L);

        InventoryDocRequestDTO.ItemDTO inboundLine = new InventoryDocRequestDTO.ItemDTO();
        inboundLine.setGoodsId(component.getId());
        inboundLine.setQty(3);
        inboundLine.setPrice(new BigDecimal("6.00"));
        InventoryDocRequestDTO inbound = new InventoryDocRequestDTO();
        inbound.setType("INBOUND");
        inbound.setRemark("GMS integration regression");
        inbound.setDetails(List.of(inboundLine));
        inventoryDocService.executeDoc(inbound);

        assertThat(goodsMapper.selectById(component.getId()).getStock()).isEqualTo(11L);
        GmsInventoryDoc inboundDoc = inventoryDocMapper.selectOne(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocType, "INBOUND")
                .eq(GmsInventoryDoc::getRemark, "GMS integration regression"));
        assertThat(inboundDoc).isNotNull();
        assertThat(inventoryDocItemMapper.selectList(new LambdaQueryWrapper<GmsInventoryDocItem>()
                .eq(GmsInventoryDocItem::getDocNo, inboundDoc.getDocNo())))
                .singleElement()
                .extracting(GmsInventoryDocItem::getDocNo,
                        GmsInventoryDocItem::getGoodsId,
                        GmsInventoryDocItem::getGoodsName,
                        GmsInventoryDocItem::getBarcode,
                        GmsInventoryDocItem::getChangeQty,
                        GmsInventoryDocItem::getCostPrice,
                        GmsInventoryDocItem::getPreStock,
                        GmsInventoryDocItem::getAfterStock,
                        GmsInventoryDocItem::getTenantId)
                .containsExactly(inboundDoc.getDocNo(), component.getId(), component.getName(), component.getBarcode(),
                        3, new BigDecimal("6.00"), 8L, 11L, 0L);
    }

    @Test
    void manualOutboundUsesAtomicStockGuardAndPersistsActualStockSnapshots() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsBrand brand = createBrand(suffix);
        GmsGoodsCategory category = createCategory(suffix);
        GmsGoods goods = createGoods(suffix, brand.getId(), category.getId(), 2L, 0, null);

        inventoryDocService.executeDoc(outbound(goods.getId(), 2, "atomic outbound"));

        assertThat(goodsMapper.selectById(goods.getId()).getStock()).isZero();
        GmsInventoryDoc doc = inventoryDocMapper.selectOne(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getDocType, "OUTBOUND")
                .eq(GmsInventoryDoc::getRemark, "atomic outbound"));
        assertThat(doc).isNotNull();
        assertThat(inventoryDocItemMapper.selectList(new LambdaQueryWrapper<GmsInventoryDocItem>()
                .eq(GmsInventoryDocItem::getDocNo, doc.getDocNo())))
                .singleElement()
                .extracting(GmsInventoryDocItem::getChangeQty, GmsInventoryDocItem::getPreStock, GmsInventoryDocItem::getAfterStock)
                .containsExactly(-2, 2L, 0L);

        assertThatThrownBy(() -> inventoryDocService.executeDoc(outbound(goods.getId(), 1, "insufficient outbound")))
                .hasMessageContaining("当前库存不足");
        assertThat(inventoryDocMapper.selectCount(new LambdaQueryWrapper<GmsInventoryDoc>()
                .eq(GmsInventoryDoc::getRemark, "insufficient outbound"))).isZero();
    }

    @Test
    void goodsDeletionRejectsReferencedComponentsAndRestoresCatalogCounters() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsBrand brand = createBrand(suffix);
        GmsGoodsCategory category = createCategory(suffix);
        GmsGoods component = createGoods(suffix + "component", brand.getId(), category.getId(), 0L, 0, null);
        GmsGoodsComboDTO componentLine = new GmsGoodsComboDTO();
        componentLine.setSubGoodsId(component.getId());
        componentLine.setSubGoodsQty(1);
        GmsGoods combo = createGoods(suffix + "combo", brand.getId(), category.getId(), 0L, 1, List.of(componentLine));

        assertThatThrownBy(() -> goodsService.delete(Set.of(component.getId())))
                .hasMessageContaining("仍被其他套餐引用");
        assertThat(goodsMapper.selectById(component.getId())).isNotNull();
        assertThat(brandMapper.selectById(brand.getId()).getGoodsCount()).isEqualTo(2);
        assertThat(categoryMapper.selectById(category.getId()).getGoodsCount()).isEqualTo(2);

        goodsService.delete(Set.of(combo.getId()));
        goodsService.delete(Set.of(component.getId()));

        assertThat(goodsMapper.selectById(combo.getId())).isNull();
        assertThat(goodsMapper.selectById(component.getId())).isNull();
        assertThat(brandMapper.selectById(brand.getId()).getGoodsCount()).isZero();
        assertThat(categoryMapper.selectById(category.getId()).getGoodsCount()).isZero();
    }

    private InventoryDocRequestDTO outbound(Long goodsId, int quantity, String remark) {
        InventoryDocRequestDTO.ItemDTO line = new InventoryDocRequestDTO.ItemDTO();
        line.setGoodsId(goodsId);
        line.setQty(quantity);
        InventoryDocRequestDTO request = new InventoryDocRequestDTO();
        request.setType("OUTBOUND");
        request.setRemark(remark);
        request.setDetails(List.of(line));
        return request;
    }

    private GmsBrand createBrand(String suffix) {
        GmsBrand brand = new GmsBrand();
        brand.setName("B" + suffix);
        brand.setDescription("integration test");
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        return brand;
    }

    private GmsGoodsCategory createCategory(String suffix) {
        GmsGoodsCategory category = new GmsGoodsCategory();
        category.setName("C" + suffix);
        category.setPid(0L);
        category.setGoodsCount(0);
        category.setTenantId(0L);
        categoryMapper.insert(category);
        return category;
    }

    private GmsGoods createGoods(String suffix, Long brandId, Long categoryId, Long stock,
                                 int isCombo, List<GmsGoodsComboDTO> subGoods) {
        GmsGoodsDTO dto = new GmsGoodsDTO();
        dto.setBrandId(brandId);
        dto.setCategoryId(categoryId);
        dto.setBarcode("G" + suffix);
        dto.setName("G" + suffix);
        dto.setUnit("piece");
        dto.setSize("standard");
        dto.setPurchasePrice(new BigDecimal("5.00"));
        dto.setSalePrice(new BigDecimal("10.00"));
        dto.setVipPrice(new BigDecimal("8.50"));
        dto.setCoupon(BigDecimal.ZERO);
        dto.setLevelPrices(Map.of("VIP", new BigDecimal("8.50")));
        dto.setLevelCoupons(Map.of("VIP", new BigDecimal("1.00")));
        dto.setStock(stock);
        dto.setIsDiscountParticipable(1);
        dto.setIsCombo(isCombo);
        dto.setSubGoodsList(subGoods);
        goodsService.add(dto, null);
        return goodsMapper.selectOne(new LambdaQueryWrapper<GmsGoods>()
                .eq(GmsGoods::getBarcode, dto.getBarcode()));
    }
}
