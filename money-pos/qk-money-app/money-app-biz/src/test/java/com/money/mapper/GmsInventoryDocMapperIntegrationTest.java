package com.money.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsInventoryDoc;
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
class GmsInventoryDocMapperIntegrationTest {

    @Autowired
    private GmsInventoryDocMapper inventoryDocMapper;

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
    void mapperCrudUsesTheGmsLocalEntityWithExplicitTableAndAssignedId() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsInventoryDoc doc = new GmsInventoryDoc();
        doc.setDocNo("AD231-" + suffix);
        doc.setDocType("INBOUND");
        doc.setTotalQty(3);
        doc.setTotalAmount(new BigDecimal("12.34"));
        doc.setOperator("mapper-test");
        doc.setRemark("inventory document mapper regression");
        doc.setTenantId(0L);

        assertThat(inventoryDocMapper.insert(doc)).isEqualTo(1);
        assertThat(doc.getId()).isNotNull();
        GmsInventoryDoc persisted = inventoryDocMapper.selectById(doc.getId());
        assertThat(persisted)
                .extracting(GmsInventoryDoc::getDocNo,
                        GmsInventoryDoc::getDocType,
                        GmsInventoryDoc::getTotalQty,
                        GmsInventoryDoc::getTotalAmount,
                        GmsInventoryDoc::getOperator,
                        GmsInventoryDoc::getRemark,
                        GmsInventoryDoc::getTenantId)
                .containsExactly(doc.getDocNo(), "INBOUND", 3, new BigDecimal("12.34"), "mapper-test",
                        "inventory document mapper regression", 0L);
        assertThat(persisted.getCreateTime()).isNotNull();
        assertThat(persisted.getUpdateTime()).isNotNull();

        doc.setTotalQty(5);
        doc.setTotalAmount(new BigDecimal("56.78"));
        doc.setRemark("updated inventory document");
        assertThat(inventoryDocMapper.updateById(doc)).isEqualTo(1);
        assertThat(inventoryDocMapper.selectById(doc.getId()))
                .extracting(GmsInventoryDoc::getTotalQty,
                        GmsInventoryDoc::getTotalAmount,
                        GmsInventoryDoc::getRemark)
                .containsExactly(5, new BigDecimal("56.78"), "updated inventory document");
    }
}
