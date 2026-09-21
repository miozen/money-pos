package com.money.feature.ums.application.member;

import com.money.contract.member.HomeMemberDistributionQuery;
import com.money.contract.member.HomeMemberDistributionSnapshot;
import com.money.dto.Home.HomeChartsVO;
import com.money.dto.Home.MemberBarVO;
import com.money.feature.gms.infrastructure.persistence.entity.GmsBrand;
import com.money.entity.UmsMember;
import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
import com.money.feature.home.application.HomeService;
import com.money.mapper.GmsBrandMapper;
import com.money.mapper.UmsMemberBrandLevelMapper;
import com.money.mapper.UmsMemberMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class HomeMemberDistributionQueryIntegrationTest {

    @Autowired
    private HomeMemberDistributionQuery homeMemberDistributionQuery;
    @Autowired
    private HomeService homeService;
    @Autowired
    private GmsBrandMapper brandMapper;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Autowired
    private UmsMemberBrandLevelMapper memberBrandLevelMapper;
    @Autowired
    private TradeFixture tradeFixture;

    @BeforeEach
    void authenticateFixtureWriter() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "N/A"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Y-tenant", "0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsOnlyActiveMembersAndKeepsHomeChartFieldsAcrossTimeRanges() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsBrand brand = new GmsBrand();
        brand.setName("会员分布品牌" + suffix);
        brand.setGoodsCount(0);
        brand.setTenantId(0L);
        brandMapper.insert(brand);
        UmsMember active = tradeFixture.createMember(suffix + "a", BigDecimal.ZERO);
        UmsMember deleted = tradeFixture.createMember(suffix + "d", BigDecimal.ZERO);
        deleted.setDeleted(true);
        memberMapper.updateById(deleted);
        insertLevel(active.getId(), brand.getId(), "VIP");
        insertLevel(deleted.getId(), brand.getId(), "VIP");

        HomeMemberDistributionSnapshot snapshot = homeMemberDistributionQuery.listActiveMemberDistribution().stream()
                .filter(item -> brand.getName().equals(item.getBrandName()) && "VIP".equals(item.getLevelCode()))
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(snapshot.getCount()).isEqualTo(1);

        HomeChartsVO today = homeService.getChartsData("today");
        HomeChartsVO month = homeService.getChartsData("month");
        assertMemberBar(today, brand.getName(), "VIP", 1);
        assertMemberBar(month, brand.getName(), "VIP", 1);
    }

    private void insertLevel(Long memberId, Long brandId, String levelCode) {
        UmsMemberBrandLevel level = new UmsMemberBrandLevel();
        level.setMemberId(memberId);
        level.setBrand(String.valueOf(brandId));
        level.setLevelCode(levelCode);
        level.setTenantId(0L);
        memberBrandLevelMapper.insert(level);
    }

    private void assertMemberBar(HomeChartsVO charts, String brandName, String levelCode, int count) {
        MemberBarVO bar = charts.getBarData().stream()
                .filter(item -> brandName.equals(item.getBrandName()) && levelCode.equals(item.getLevelCode()))
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(bar.getCount()).isEqualTo(count);
    }
}
