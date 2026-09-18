package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.PosMemberLevel;
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
class PosMemberLevelMapperIntegrationTest {

    @Autowired
    private PosMemberLevelMapper memberLevelMapper;

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
    void mapperCrudUsesTheUmsLocalEntityWithExistingTableAndTenantFields() {
        String suffix = Long.toString(System.nanoTime(), 36);
        PosMemberLevel level = new PosMemberLevel();
        level.setLevelName("LEVEL-" + suffix);
        level.setTenantId("0");

        assertThat(memberLevelMapper.insert(level)).isEqualTo(1);
        assertThat(level.getId()).isNotNull();
        assertThat(memberLevelMapper.selectById(level.getId()))
                .extracting(PosMemberLevel::getLevelName, PosMemberLevel::getTenantId)
                .containsExactly("LEVEL-" + suffix, "0");

        level.setLevelName("UPDATED-" + suffix);
        assertThat(memberLevelMapper.updateById(level)).isEqualTo(1);
        assertThat(memberLevelMapper.selectById(level.getId()).getLevelName()).isEqualTo("UPDATED-" + suffix);

        assertThat(memberLevelMapper.deleteById(level.getId())).isEqualTo(1);
        assertThat(memberLevelMapper.selectById(level.getId())).isNull();
    }
}
