package com.money.mapper;

import com.money.feature.home.infrastructure.persistence.entity.OmsDailySummary;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class OmsDailySummaryMapperIntegrationTest {

    @Autowired
    private OmsDailySummaryMapper dailySummaryMapper;

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
    void mapperCrudUsesTheHomeLocalEntityWithExplicitTableAndSnapshotFields() {
        OmsDailySummary summary = snapshot(uniqueDate(), "12.34", 5, "4.56", "2.47", "78.90", "9.87", 3);

        assertThat(dailySummaryMapper.insert(summary)).isEqualTo(1);
        assertThat(summary.getId()).isNotNull();
        assertThat(dailySummaryMapper.selectById(summary.getId()))
                .extracting(OmsDailySummary::getRecordDate,
                        OmsDailySummary::getSalesAmount,
                        OmsDailySummary::getOrderCount,
                        OmsDailySummary::getProfitAmount,
                        OmsDailySummary::getAsp,
                        OmsDailySummary::getInventoryValue,
                        OmsDailySummary::getMemberRecharge,
                        OmsDailySummary::getNewMemberCount)
                .containsExactly(summary.getRecordDate(), new BigDecimal("12.34"), 5,
                        new BigDecimal("4.56"), new BigDecimal("2.47"), new BigDecimal("78.90"),
                        new BigDecimal("9.87"), 3);

        summary.setSalesAmount(new BigDecimal("23.45"));
        summary.setMemberRecharge(new BigDecimal("10.11"));
        summary.setNewMemberCount(4);
        assertThat(dailySummaryMapper.updateById(summary)).isEqualTo(1);
        assertThat(dailySummaryMapper.selectById(summary.getId()))
                .extracting(OmsDailySummary::getSalesAmount,
                        OmsDailySummary::getMemberRecharge,
                        OmsDailySummary::getNewMemberCount)
                .containsExactly(new BigDecimal("23.45"), new BigDecimal("10.11"), 4);

        assertThat(dailySummaryMapper.deleteById(summary.getId())).isEqualTo(1);
        assertThat(dailySummaryMapper.selectById(summary.getId())).isNull();
    }

    @Test
    void atomicWritesKeepOneDateRecordAndPreserveMemberRecharge() {
        LocalDate recordDate = uniqueDate();
        OmsDailySummary existing = snapshot(recordDate, "10.00", 1, "2.00", "10.00", "20.00", "7.00", 1);
        assertThat(dailySummaryMapper.insert(existing)).isEqualTo(1);
        Long id = existing.getId();

        dailySummaryMapper.insertIfAbsent(snapshot(recordDate, "99.00", 9, "9.00", "11.00", "30.00", "88.00", 9));
        assertThat(dailySummaryMapper.selectById(id))
                .extracting(OmsDailySummary::getSalesAmount,
                        OmsDailySummary::getOrderCount,
                        OmsDailySummary::getMemberRecharge)
                .containsExactly(new BigDecimal("10.00"), 1, new BigDecimal("7.00"));

        dailySummaryMapper.upsertSnapshot(snapshot(recordDate, "30.00", 3, "6.00", "10.00", "40.00", "99.00", 2));
        OmsDailySummary refreshed = dailySummaryMapper.selectById(id);
        assertThat(refreshed.getId()).isEqualTo(id);
        assertThat(refreshed)
                .extracting(OmsDailySummary::getSalesAmount,
                        OmsDailySummary::getOrderCount,
                        OmsDailySummary::getProfitAmount,
                        OmsDailySummary::getAsp,
                        OmsDailySummary::getInventoryValue,
                        OmsDailySummary::getNewMemberCount,
                        OmsDailySummary::getMemberRecharge)
                .containsExactly(new BigDecimal("30.00"), 3, new BigDecimal("6.00"), new BigDecimal("10.00"),
                        new BigDecimal("40.00"), 2, new BigDecimal("7.00"));
    }

    private LocalDate uniqueDate() {
        return LocalDate.of(2100, 1, 1).minusDays(System.nanoTime() & 0xFFFF);
    }

    private OmsDailySummary snapshot(LocalDate recordDate, String salesAmount, int orderCount, String profitAmount,
                                     String asp, String inventoryValue, String memberRecharge, int newMemberCount) {
        OmsDailySummary summary = new OmsDailySummary();
        summary.setRecordDate(recordDate);
        summary.setSalesAmount(new BigDecimal(salesAmount));
        summary.setOrderCount(orderCount);
        summary.setProfitAmount(new BigDecimal(profitAmount));
        summary.setAsp(new BigDecimal(asp));
        summary.setInventoryValue(new BigDecimal(inventoryValue));
        summary.setMemberRecharge(new BigDecimal(memberRecharge));
        summary.setNewMemberCount(newMemberCount);
        return summary;
    }
}
