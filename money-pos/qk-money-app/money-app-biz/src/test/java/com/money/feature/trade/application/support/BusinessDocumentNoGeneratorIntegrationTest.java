package com.money.feature.trade.application.support;

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
class BusinessDocumentNoGeneratorIntegrationTest {

    @Autowired
    private BusinessDocumentNoGenerator generator;

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
    void firstDocumentOfEachPrefixStartsAtOneRegardlessOfSequenceRowId() {
        long token = System.nanoTime();
        String firstPrefix = testPrefix(token);
        String secondPrefix = testPrefix(token + 1);

        String first = generator.next(firstPrefix);
        String second = generator.next(secondPrefix);
        String firstNext = generator.next(firstPrefix);

        assertThat(first).matches(firstPrefix + "\\d{18}").endsWith("0001");
        assertThat(second).matches(secondPrefix + "\\d{18}").endsWith("0001");
        assertThat(firstNext).matches(firstPrefix + "\\d{18}").endsWith("0002");
    }

    private String testPrefix(long token) {
        StringBuilder prefix = new StringBuilder("T");
        for (int index = 0; index < 5; index++) {
            prefix.append((char) ('A' + Math.floorMod(token, 26)));
            token /= 26;
        }
        return prefix.toString();
    }
}
