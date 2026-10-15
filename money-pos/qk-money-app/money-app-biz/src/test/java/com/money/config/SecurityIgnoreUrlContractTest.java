package com.money.config;

import com.money.security.config.IgnoreUrlConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps customer-display reads public without exposing asset or configuration writes anonymously. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityIgnoreUrlContractTest {

    @Autowired
    private IgnoreUrlConfig ignoreUrlConfig;

    @Test
    void customerDisplayReadRemainsPublicWhileWritesRequireAuthentication() {
        assertThat(ignoreUrlConfig.getGet()).contains("/common/display-settings");
        assertThat(ignoreUrlConfig.getPost()).doesNotContain("/common/upload");
        assertThat(ignoreUrlConfig.getPut()).doesNotContain("/common/display-settings");
    }
}
