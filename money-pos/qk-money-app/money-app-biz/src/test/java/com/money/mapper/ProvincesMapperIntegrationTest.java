package com.money.mapper;

import com.money.feature.sys.infrastructure.persistence.entity.Provinces;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class ProvincesMapperIntegrationTest {

    @Autowired
    private ProvincesMapper provincesMapper;

    @Test
    void readsTheFlywaySeededBeijingDistrictWithoutWritingTheDictionaryTable() {
        Provinces beijing = provincesMapper.selectAll().stream()
                .filter(province -> "110100".equals(province.getDistrictId()))
                .findFirst()
                .orElseThrow();

        assertThat(beijing)
                .extracting(Provinces::getDistrictId,
                        Provinces::getProvince,
                        Provinces::getCity,
                        Provinces::getCityGeocode,
                        Provinces::getDistrict,
                        Provinces::getDistrictGeocode,
                        Provinces::getLon,
                        Provinces::getLat)
                .containsExactly("110100", "北京市", "北京市", "110100", "北京", "110100",
                        "116.405285", "39.904989");
    }
}
