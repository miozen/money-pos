package com.money.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.PosSkuLevelPrice;
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
class PosSkuLevelPriceMapperIntegrationTest {

    @Autowired
    private PosSkuLevelPriceMapper levelPriceMapper;

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
    void mapperCrudUsesTheGmsLocalEntityWithExplicitTableAndAutoId() {
        String suffix = Long.toString(System.nanoTime(), 36);
        PosSkuLevelPrice price = new PosSkuLevelPrice();
        price.setSkuId(90000001L);
        price.setLevelId("AD233-" + suffix);
        price.setMemberPrice(new BigDecimal("12.34"));
        price.setMemberCoupon(new BigDecimal("1.23"));
        price.setTenantId("0");

        assertThat(levelPriceMapper.insert(price)).isEqualTo(1);
        assertThat(price.getId()).isNotNull();
        assertThat(levelPriceMapper.selectById(price.getId()))
                .extracting(PosSkuLevelPrice::getSkuId,
                        PosSkuLevelPrice::getLevelId,
                        PosSkuLevelPrice::getMemberPrice,
                        PosSkuLevelPrice::getMemberCoupon,
                        PosSkuLevelPrice::getTenantId)
                .containsExactly(90000001L, price.getLevelId(), new BigDecimal("12.34"),
                        new BigDecimal("1.23"), "0");

        price.setMemberPrice(new BigDecimal("56.78"));
        price.setMemberCoupon(BigDecimal.ZERO);
        assertThat(levelPriceMapper.updateById(price)).isEqualTo(1);
        assertThat(levelPriceMapper.selectById(price.getId()))
                .extracting(PosSkuLevelPrice::getMemberPrice, PosSkuLevelPrice::getMemberCoupon)
                .containsExactly(new BigDecimal("56.78"), new BigDecimal("0.00"));

        assertThat(levelPriceMapper.deleteById(price.getId())).isEqualTo(1);
        assertThat(levelPriceMapper.selectById(price.getId())).isNull();
    }
}
