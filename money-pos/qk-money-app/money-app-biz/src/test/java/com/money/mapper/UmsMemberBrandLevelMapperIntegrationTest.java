package com.money.mapper;

import com.money.feature.ums.infrastructure.persistence.entity.UmsMemberBrandLevel;
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
class UmsMemberBrandLevelMapperIntegrationTest {

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
    void mapperCrudUsesTheUmsLocalEntityWithExplicitTableAutoIdAndBrandLevelMatrix() {
        String suffix = Long.toString(System.nanoTime(), 36);
        UmsMemberBrandLevel level = new UmsMemberBrandLevel();
        level.setMemberId(90000001L);
        level.setBrand("AD235-" + suffix);
        level.setLevelCode("LEVEL-" + suffix);
        level.setTenantId(0L);

        assertThat(memberBrandLevelMapper.insert(level)).isEqualTo(1);
        assertThat(level.getId()).isNotNull();
        UmsMemberBrandLevel persisted = memberBrandLevelMapper.selectById(level.getId());
        assertThat(persisted)
                .extracting(UmsMemberBrandLevel::getMemberId,
                        UmsMemberBrandLevel::getBrand,
                        UmsMemberBrandLevel::getLevelCode,
                        UmsMemberBrandLevel::getTenantId)
                .containsExactly(90000001L, level.getBrand(), level.getLevelCode(), 0L);
        assertThat(persisted.getCreateTime()).isNotNull();
        assertThat(persisted.getUpdateTime()).isNotNull();

        level.setLevelCode("UPDATED-" + suffix);
        assertThat(memberBrandLevelMapper.updateById(level)).isEqualTo(1);
        assertThat(memberBrandLevelMapper.selectById(level.getId()))
                .extracting(UmsMemberBrandLevel::getLevelCode,
                        UmsMemberBrandLevel::getMemberId,
                        UmsMemberBrandLevel::getBrand)
                .containsExactly("UPDATED-" + suffix, 90000001L, level.getBrand());

        assertThat(memberBrandLevelMapper.deleteById(level.getId())).isEqualTo(1);
        assertThat(memberBrandLevelMapper.selectById(level.getId())).isNull();
    }
}
