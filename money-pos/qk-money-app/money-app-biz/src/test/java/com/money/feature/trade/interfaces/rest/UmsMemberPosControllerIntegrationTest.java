package com.money.feature.trade.interfaces.rest;

import com.money.feature.trade.application.pos.dto.CouponRuleSummary;
import com.money.dto.pos.PosMemberVO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.feature.ums.infrastructure.persistence.entity.PosCouponRule;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class UmsMemberPosControllerIntegrationTest {

    @Autowired
    private UmsMemberPosController umsMemberPosController;

    @Autowired
    private TradeFixture tradeFixture;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private UmsMemberBrandLevelMapper memberBrandLevelMapper;

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
    void couponRuleRouteReturnsTheStableSummaryContract() {
        String suffix = Long.toString(System.nanoTime(), 36);
        tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));

        List<CouponRuleSummary> rules = umsMemberPosController.getCouponRules();

        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.getName()).isEqualTo("C" + suffix);
            assertThat(rule.getThresholdAmount()).isEqualByComparingTo("20.00");
            assertThat(rule.getDiscountAmount()).isEqualByComparingTo("5.00");
            assertThat(rule.getStatus()).isEqualTo(1);
        });
    }

    @Test
    void posSearchReturnsMemberSnapshotWithBalanceAndBrandLevels() {
        String suffix = Long.toString(System.nanoTime(), 36);
        com.money.feature.ums.infrastructure.persistence.entity.UmsMember member = tradeFixture.createMember(suffix, new BigDecimal("18.00"));
        PosCouponRule rule = tradeFixture.createCouponRule(suffix, new BigDecimal("20.00"), new BigDecimal("5.00"));
        tradeFixture.issueCoupon(member.getId(), rule.getId());
        GmsBrand brand = new GmsBrand();
        brand.setName("POS品牌" + suffix);
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        UmsMemberBrandLevel level = new UmsMemberBrandLevel();
        level.setMemberId(member.getId());
        level.setBrand(String.valueOf(brand.getId()));
        level.setLevelCode("VIP");
        level.setTenantId(0L);
        memberBrandLevelMapper.insert(level);

        List<PosMemberVO> members = umsMemberPosController.posSearchMember("M" + suffix);

        assertThat(members).anySatisfy(found -> {
            assertThat(found.getId()).isEqualTo(member.getId());
            assertThat(found.getName()).isEqualTo(member.getName());
            assertThat(found.getPhone()).isEqualTo(member.getPhone());
            assertThat(found.getBalance()).isEqualByComparingTo("18.00");
            assertThat(found.getBrandLevels()).containsEntry(String.valueOf(brand.getId()), "VIP");
            assertThat(found.getBrandLevelDesc()).containsEntry("POS品牌" + suffix, "VIP");
            assertThat(found.getVoucherCount()).isEqualTo(1);
            assertThat(found.getCouponList()).singleElement().satisfies(coupon -> {
                assertThat(coupon.getRuleId()).isEqualTo(rule.getId());
                assertThat(coupon.getThreshold()).isEqualByComparingTo("20.00");
                assertThat(coupon.getDeduction()).isEqualByComparingTo("5.00");
                assertThat(coupon.getAvailableCount()).isEqualTo(1);
            });
        });
    }
}
