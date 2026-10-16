package com.money.security;

import com.money.security.component.SecurityTokenSupport;
import com.money.security.component.TokenStrategy;
import com.money.security.config.TokenConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityTokenSupportContractTest {

    @Test
    void invalidatingUsernameClearsAccessAndRefreshKeys() {
        TokenConfig config = new TokenConfig();
        config.setCacheKey("test:token:");
        List<String> invalidatedKeys = new ArrayList<>();
        TokenStrategy strategy = new TokenStrategy() {
            @Override public void saveToken(String key, String token, long timeout, TimeUnit unit) { }
            @Override public boolean isExpired(String key, String targetToken) { return false; }
            @Override public void invalidateToken(String key, String token) { invalidatedKeys.add(key); }
        };

        new SecurityTokenSupport(config, strategy, "test").invalidateTokensForUsername("old-name");

        assertThat(invalidatedKeys).containsExactly("test:token:old-name:string", "test:token::refresh:old-name:string");
    }
}
