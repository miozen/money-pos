package com.money.controller;

import com.money.service.ReleaseVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class SysVersionControllerContractTest {
    @Test
    void authenticatedSystemVersionRouteReturnsTheEmbeddedBuildVersion() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("version", "1.2.3");
        SysVersionController controller = new SysVersionController(new ReleaseVersionService(new BuildProperties(properties)));

        assertThat(controller.currentVersion()).containsExactlyEntriesOf(java.util.Map.of("version", "1.2.3"));
        assertThat(SysVersionController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/system/version");
        assertThat(SysVersionController.class.getDeclaredMethod("currentVersion").getAnnotation(GetMapping.class)).isNotNull();
        assertThat(SysVersionController.class.getDeclaredMethod("currentVersion").getAnnotation(PreAuthorize.class).value())
                .isEqualTo("isAuthenticated()");
    }
}
