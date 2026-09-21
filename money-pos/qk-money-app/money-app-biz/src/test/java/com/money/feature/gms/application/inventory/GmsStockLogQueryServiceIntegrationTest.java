package com.money.feature.gms.application.inventory;

import com.money.dto.GmsGoods.GmsStockLogQueryDTO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsStockLog;
import com.money.mapper.GmsStockLogMapper;
import com.money.web.vo.PageVO;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsStockLogQueryServiceIntegrationTest {

    @Autowired
    private GmsStockLogQueryService gmsStockLogQueryService;

    @Autowired
    private GmsStockLogMapper gmsStockLogMapper;

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
    void filtersPagedStockLogsByExactBarcodeAndExistingCriteria() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsStockLog expected = insertLog("BC-" + suffix, "Tea " + suffix, "INBOUND", "IN-" + suffix);
        insertLog("OTHER-" + suffix, "Tea " + suffix, "INBOUND", "IN-" + suffix);

        GmsStockLogQueryDTO query = new GmsStockLogQueryDTO();
        query.setPage(1L);
        query.setSize(10L);
        query.setGoodsName("Tea " + suffix);
        query.setType("INBOUND");
        query.setOrderNo("IN-" + suffix);

        PageVO<GmsStockLog> result = gmsStockLogQueryService.list(query, expected.getGoodsBarcode());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting(GmsStockLog::getId)
                .isEqualTo(expected.getId());
    }

    private GmsStockLog insertLog(String barcode, String name, String type, String orderNo) {
        GmsStockLog log = new GmsStockLog();
        log.setGoodsId(System.nanoTime());
        log.setGoodsBarcode(barcode);
        log.setGoodsName(name);
        log.setType(type);
        log.setQuantity(1);
        log.setAfterQuantity(1);
        log.setOrderNo(orderNo);
        log.setCreateTime(LocalDateTime.now());
        log.setTenantId(0L);
        gmsStockLogMapper.insert(log);
        return log;
    }
}
