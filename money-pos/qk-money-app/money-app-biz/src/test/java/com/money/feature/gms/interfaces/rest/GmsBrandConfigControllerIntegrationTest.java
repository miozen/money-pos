package com.money.feature.gms.interfaces.rest;

import com.money.dto.SysBrandConfig.BrandPricingPolicy;
import com.money.dto.SysBrandConfig.BrandPricingPolicyView;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsBrandConfigControllerIntegrationTest {

    @Autowired
    private GmsBrandConfigController gmsBrandConfigController;

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
    void preservesDefaultCreateUpdateAndEmptyLevelCompatibility() {
        String brand = "stage4-brand-" + Long.toString(System.nanoTime(), 36);

        BrandPricingPolicyView unconfigured = gmsBrandConfigController.getConfig(brand);
        assertThat(unconfigured.getCouponEnabled()).isTrue();
        assertThat(unconfigured.getLevelCodes()).isNull();

        gmsBrandConfigController.saveConfig(new BrandPricingPolicy(brand, false, "V1,V2"));
        BrandPricingPolicyView configured = gmsBrandConfigController.getConfig(brand);
        assertThat(configured.getCouponEnabled()).isFalse();
        assertThat(configured.getLevelCodes()).containsExactly("V1", "V2");

        gmsBrandConfigController.saveConfig(new BrandPricingPolicy(brand, true, ""));
        BrandPricingPolicyView updated = gmsBrandConfigController.getConfig(brand);
        assertThat(updated.getCouponEnabled()).isTrue();
        assertThat(updated.getLevelCodes()).isEmpty();
    }
}
