package com.money.feature.ums.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

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

    @Test
    void configurationWriteHasDedicatedCapabilityAndLoopbackGuard() throws Exception {
        java.lang.reflect.Method save = java.util.Arrays.stream(UmsMemberCloudSyncController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("saveConfiguration")).findFirst().orElseThrow();
        assertThat(save.getAnnotation(PostMapping.class).value()).containsExactly("/config");
        assertThat(save.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')");
        assertThat(UmsMemberCloudSyncController.class.getDeclaredMethod("requireLoopback", javax.servlet.http.HttpServletRequest.class).getReturnType()).isEqualTo(void.class);
    }

    @Test
    void localOperationalCommandsUseTheSameProtectedBoundary() {
        assertProtected("revealSecrets", "/config/reveal-secrets");
        assertProtected("beginRepair", "/repair");
        assertProtected("dispatchNow", "/dispatch");
        assertProtected("testConnection", "/test-connection");
    }

    private void assertProtected(String methodName, String route) {
        java.lang.reflect.Method method = java.util.Arrays.stream(UmsMemberCloudSyncController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(route);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("@rbac.hasRole('SUPER_ADMIN') and @rbac.hasPermission('omniaCloud:manage')");
    }
}
