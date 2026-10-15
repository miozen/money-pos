package com.money.feature.fin.interfaces.rest;

import com.money.dto.Finance.FinanceWaterfallQueryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceReportRoutePermissionContractTest {

    @Test
    void waterfallDailyUsesExistingOrderReadPermission() throws Exception {
        Method method = FinanceReportController.class.getMethod("getDailyWaterfallReport", FinanceWaterfallQueryDTO.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/waterfall/daily");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@rbac.hasPermission('omsOrder:list')");
    }
}
