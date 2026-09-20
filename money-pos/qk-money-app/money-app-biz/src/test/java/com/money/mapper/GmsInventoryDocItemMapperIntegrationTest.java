package com.money.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDocItem;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsInventoryDocItemMapperIntegrationTest {

    @Autowired
    private GmsInventoryDocItemMapper inventoryDocItemMapper;

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
    void mapperCrudUsesTheGmsLocalEntityWithExplicitTableAuditAndSnapshotFields() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsInventoryDocItem item = new GmsInventoryDocItem();
        item.setDocNo("AD211-" + suffix);
        item.setGoodsId(90000001L);
        item.setGoodsName("GMS inventory snapshot " + suffix);
        item.setBarcode("DOC-" + suffix);
        item.setChangeQty(3);
        item.setCostPrice(new BigDecimal("12.34"));
        item.setPreStock(7L);
        item.setAfterStock(10L);
        item.setTenantId(0L);

        assertThat(inventoryDocItemMapper.insert(item)).isEqualTo(1);
        assertThat(item.getId()).isNotNull();
        GmsInventoryDocItem persisted = inventoryDocItemMapper.selectById(item.getId());
        assertThat(persisted)
                .extracting(GmsInventoryDocItem::getDocNo,
                        GmsInventoryDocItem::getGoodsId,
                        GmsInventoryDocItem::getGoodsName,
                        GmsInventoryDocItem::getBarcode,
                        GmsInventoryDocItem::getChangeQty,
                        GmsInventoryDocItem::getCostPrice,
                        GmsInventoryDocItem::getPreStock,
                        GmsInventoryDocItem::getAfterStock,
                        GmsInventoryDocItem::getTenantId)
                .containsExactly(item.getDocNo(), 90000001L, item.getGoodsName(), item.getBarcode(), 3,
                        new BigDecimal("12.34"), 7L, 10L, 0L);
        assertThat(persisted.getCreateTime()).isNotNull();

        item.setGoodsName("updated " + suffix);
        item.setChangeQty(-2);
        item.setCostPrice(new BigDecimal("56.78"));
        item.setAfterStock(8L);
        assertThat(inventoryDocItemMapper.updateById(item)).isEqualTo(1);
        assertThat(inventoryDocItemMapper.selectById(item.getId()))
                .extracting(GmsInventoryDocItem::getGoodsName,
                        GmsInventoryDocItem::getChangeQty,
                        GmsInventoryDocItem::getCostPrice,
                        GmsInventoryDocItem::getAfterStock)
                .containsExactly("updated " + suffix, -2, new BigDecimal("56.78"), 8L);

        assertThat(inventoryDocItemMapper.deleteById(item.getId())).isEqualTo(1);
        assertThat(inventoryDocItemMapper.selectById(item.getId())).isNull();
    }
}
