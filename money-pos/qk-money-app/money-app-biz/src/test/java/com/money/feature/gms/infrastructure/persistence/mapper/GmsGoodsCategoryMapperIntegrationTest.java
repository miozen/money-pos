package com.money.feature.gms.infrastructure.persistence.mapper;

import com.money.feature.gms.infrastructure.persistence.entity.GmsGoodsCategory;
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
class GmsGoodsCategoryMapperIntegrationTest {
    @Autowired private GmsGoodsCategoryMapper categoryMapper;
    @BeforeEach void authenticateTenant() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "N/A")); MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("Y-tenant", "0"); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); }
    @AfterEach void clearTenant() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }
    @Test void mapperCrudUsesTheGmsLocalEntityWithBaseEntityAssignedIdAndCategoryFields() {
        String suffix = Long.toString(System.nanoTime(), 36);
        GmsGoodsCategory category = new GmsGoodsCategory();
        category.setPid(0L); category.setIcon("category/" + suffix); category.setName("AD237-" + suffix); category.setGoodsCount(2); category.setTenantId(0L);
        assertThat(categoryMapper.insert(category)).isEqualTo(1); assertThat(category.getId()).isNotNull();
        GmsGoodsCategory persisted = categoryMapper.selectById(category.getId());
        assertThat(persisted).extracting(GmsGoodsCategory::getPid, GmsGoodsCategory::getIcon, GmsGoodsCategory::getName, GmsGoodsCategory::getGoodsCount, GmsGoodsCategory::getTenantId).containsExactly(0L, category.getIcon(), category.getName(), 2, 0L);
        assertThat(persisted.getCreateTime()).isNotNull(); assertThat(persisted.getUpdateTime()).isNotNull();
        category.setGoodsCount(3); category.setIcon("category/updated-" + suffix); assertThat(categoryMapper.updateById(category)).isEqualTo(1);
        assertThat(categoryMapper.selectById(category.getId())).extracting(GmsGoodsCategory::getGoodsCount, GmsGoodsCategory::getIcon).containsExactly(3, category.getIcon());
        assertThat(categoryMapper.deleteById(category.getId())).isEqualTo(1); assertThat(categoryMapper.selectById(category.getId())).isNull();
    }
}
