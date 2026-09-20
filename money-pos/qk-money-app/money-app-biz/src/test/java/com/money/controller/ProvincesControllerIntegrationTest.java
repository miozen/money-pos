package com.money.controller;

import com.money.dto.SelectVO;
import com.money.service.ProvincesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class ProvincesControllerIntegrationTest {

    @Autowired
    private ProvincesController provincesController;

    @Autowired
    private ProvincesService provincesService;

    @BeforeEach
    void bindRequestForTheExistingControllerLoggingAspect() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/provinces");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsSelectVoDictionaryValuesAndKeepsTheInitializedJvmCacheForRepeatedReads() {
        List<SelectVO> firstProvinces = provincesController.listProvinces();

        assertThat(firstProvinces)
                .extracting(SelectVO::getLabel, SelectVO::getValue)
                .contains(tuple("北京市", "北京市"));
        assertThat(provincesService.listProvinces()).isSameAs(firstProvinces);
        assertThat(provincesController.listCities("北京市"))
                .extracting(SelectVO::getLabel, SelectVO::getValue)
                .contains(tuple("北京市", "北京市"));
        assertThat(provincesController.listDistricts("北京市"))
                .extracting(SelectVO::getLabel, SelectVO::getValue)
                .contains(tuple("北京", "北京"));
    }
}
