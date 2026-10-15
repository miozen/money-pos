package com.money.feature.trade.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OmsOrderRoutePermissionContractTest {

    @Test
    void hardwareReprintUsesExistingOrderReadPermission() throws Exception {
        Method method = OmsOrderController.class.getMethod("printHardwareReceipt", String.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/hardware/print");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@rbac.hasPermission('oms:order:list')");
    }
}
