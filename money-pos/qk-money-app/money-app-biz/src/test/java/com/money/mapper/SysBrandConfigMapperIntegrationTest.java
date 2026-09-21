package com.money.mapper;

import com.money.feature.sys.infrastructure.persistence.entity.SysBrandConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class SysBrandConfigMapperIntegrationTest {

    @Autowired
    private SysBrandConfigMapper brandConfigMapper;

    @Test
    void mapperCrudUsesSysLocalBrandConfigEntity() {
        SysBrandConfig config = new SysBrandConfig();
        config.setBrand("brand-config-" + System.nanoTime());
        config.setCouponEnabled(true);
        config.setLevelCodes("VIP,GOLD");
        config.setTenantId(0L);

        assertThat(brandConfigMapper.insert(config)).isEqualTo(1);
        assertThat(config.getId()).isNotNull();

        SysBrandConfig persisted = brandConfigMapper.selectById(config.getId());
        assertThat(persisted).extracting(SysBrandConfig::getBrand, SysBrandConfig::getCouponEnabled,
                        SysBrandConfig::getLevelCodes, SysBrandConfig::getTenantId)
                .containsExactly(config.getBrand(), true, "VIP,GOLD", 0L);

        config.setCouponEnabled(false);
        config.setLevelCodes("VIP");
        assertThat(brandConfigMapper.updateById(config)).isEqualTo(1);
        assertThat(brandConfigMapper.selectById(config.getId()))
                .extracting(SysBrandConfig::getCouponEnabled, SysBrandConfig::getLevelCodes)
                .containsExactly(false, "VIP");
        assertThat(brandConfigMapper.deleteById(config.getId())).isEqualTo(1);
    }
}
