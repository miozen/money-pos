package com.money.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCombo;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class GmsGoodsComboMapperIntegrationTest {

    @Autowired
    private GmsGoodsComboMapper goodsComboMapper;

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
    void mapperCrudUsesTheGmsLocalEntityWithImplicitTableAndAssignedId() {
        LocalDateTime createTime = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        GmsGoodsCombo combo = new GmsGoodsCombo();
        combo.setComboGoodsId(90000001L);
        combo.setSubGoodsId(90000002L);
        combo.setSubGoodsQty(2);
        combo.setCreateTime(createTime);

        assertThat(goodsComboMapper.insert(combo)).isEqualTo(1);
        assertThat(combo.getId()).isNotNull();
        assertThat(goodsComboMapper.selectById(combo.getId()))
                .extracting(GmsGoodsCombo::getComboGoodsId,
                        GmsGoodsCombo::getSubGoodsId,
                        GmsGoodsCombo::getSubGoodsQty,
                        GmsGoodsCombo::getCreateTime)
                .containsExactly(90000001L, 90000002L, 2, createTime);

        combo.setSubGoodsQty(3);
        assertThat(goodsComboMapper.updateById(combo)).isEqualTo(1);
        assertThat(goodsComboMapper.selectById(combo.getId()))
                .extracting(GmsGoodsCombo::getSubGoodsQty)
                .isEqualTo(3);

        assertThat(goodsComboMapper.deleteById(combo.getId())).isEqualTo(1);
        assertThat(goodsComboMapper.selectById(combo.getId())).isNull();
    }
}
