package com.money.feature.ums.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;

class UmsMemberCloudSyncRouteContractTest {
    @Test
    void statusRouteIsDedicatedAndReadOnly() throws Exception {
        java.lang.reflect.Method status = UmsMemberCloudSyncController.class.getDeclaredMethod("status");
        assertThat(status.getAnnotation(GetMapping.class).value()).containsExactly("/status");
        assertThat(status.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasPermission('omniaCloud:view')");
        assertThat(status.getReturnType().getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .doesNotContain("endpoint", "signingSecretBase64", "searchHmacSecretBase64");
    }
}
