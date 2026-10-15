package com.money.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SysBackupRoutePermissionContractTest {

    @Test
    void exportAndRestoreRequireTheExistingSuperAdministratorRole() throws Exception {
        Method export = SysBackupController.class.getMethod("exportBackup", HttpServletResponse.class);
        assertThat(export.getAnnotation(GetMapping.class).value()).containsExactly("/export");
        assertSuperAdministratorOnly(export);

        Method restore = SysBackupController.class.getMethod("restoreBackup", MultipartFile.class);
        assertThat(restore.getAnnotation(PostMapping.class).value()).containsExactly("/restore");
        assertSuperAdministratorOnly(restore);
    }

    private void assertSuperAdministratorOnly(Method method) {
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@rbac.hasRole('SUPER_ADMIN')");
    }
}
