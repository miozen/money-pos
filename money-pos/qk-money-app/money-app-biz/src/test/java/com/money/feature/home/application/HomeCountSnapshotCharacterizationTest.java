package com.money.feature.home.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.money.contract.goods.InventoryValuationQuery;
import com.money.dto.Home.HomeCountVO;
import com.money.feature.home.application.HomeService;
import com.money.feature.home.interfaces.rest.HomeController;
import com.money.entity.OmsDailySummary;
import com.money.mapper.OmsDailySummaryMapper;
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
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterizes the existing write-on-read behavior of GET /home/count before HOME types move.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class HomeCountSnapshotCharacterizationTest {

    @Autowired
    private HomeController homeController;

    @Autowired
    private OmsDailySummaryMapper dailySummaryMapper;
    @Autowired
    private InventoryValuationQuery inventoryValuationQuery;
    @Autowired
    private HomeService homeService;

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
    void homeCountCreatesThenUpdatesTodaySnapshotAndKeepsDashboardShape() {
        LocalDate today = LocalDate.now();
        dailySummaryMapper.delete(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, today));

        Map<String, Object> firstResponse = homeController.homeCountVO();
        BigDecimal expectedInventoryValue = inventoryValuationQuery.getCurrentStockValue();

        assertThat(firstResponse).containsOnlyKeys("today", "month", "year", "total", "inventoryValue", "alerts");
        assertThat(firstResponse.get("today")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("month")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("year")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("total")).isInstanceOf(Map.class);
        assertThat(firstResponse.get("alerts")).isInstanceOf(java.util.List.class);

        OmsDailySummary firstSnapshot = todaySnapshot(today);
        assertThat(firstSnapshot).isNotNull();
        assertThat((BigDecimal) firstResponse.get("inventoryValue")).isEqualByComparingTo(expectedInventoryValue);
        assertThat(firstSnapshot.getInventoryValue()).isEqualByComparingTo(expectedInventoryValue);
        HomeCountVO homeCount = homeService.homeCount();
        assertThat(homeCount.getInventoryValue()).isEqualByComparingTo(expectedInventoryValue);
        Long snapshotId = firstSnapshot.getId();

        firstSnapshot.setSalesAmount(new BigDecimal("-1.00"));
        dailySummaryMapper.updateById(firstSnapshot);

        Map<String, Object> secondResponse = homeController.homeCountVO();
        OmsDailySummary updatedSnapshot = todaySnapshot(today);

        assertThat(secondResponse).containsOnlyKeys("today", "month", "year", "total", "inventoryValue", "alerts");
        assertThat(updatedSnapshot.getId()).isEqualTo(snapshotId);
        assertThat(updatedSnapshot.getSalesAmount()).isNotEqualByComparingTo("-1.00");
        assertThat(dailySummaryMapper.selectCount(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, today))).isEqualTo(1);
    }

    private OmsDailySummary todaySnapshot(LocalDate date) {
        return dailySummaryMapper.selectOne(new LambdaQueryWrapper<OmsDailySummary>()
                .eq(OmsDailySummary::getRecordDate, date)
                .last("LIMIT 1"));
    }
}
