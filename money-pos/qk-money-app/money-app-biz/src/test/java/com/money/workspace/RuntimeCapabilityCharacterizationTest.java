package com.money.workspace;

import com.money.QkMoneyApplication;
import com.money.platform.runtime.workspace.RuntimeWorkspace;
import com.money.platform.runtime.workspace.RuntimeWorkspaceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCapabilityCharacterizationTest {

    private static final String[] MANAGED_PROPERTIES = {
            "app.data", "app.home", "money.workspace.embedded",
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.datasource.driver-class-name", "local.bucket", "money.cache.local.provider"
    };

    private final Map<String, String> originalProperties = captureProperties();

    @AfterEach
    void restoreGlobalState() throws Exception {
        restoreProperties();
        setStaticField(RuntimeWorkspace.class, "appHome", null);
        setStaticField(RuntimeWorkspace.class, "appData", null);
        setStaticField(MariaDbGuardian.class, "dbPassword", "");
    }

    @Test
    void embeddedWorkspaceStartsOnlyForExplicitDesktopModes() throws Exception {
        System.clearProperty("money.workspace.embedded");
        assertThat(shouldStartEmbeddedWorkspace()).isFalse();
        assertThat(shouldStartEmbeddedWorkspace("--spring.profiles.active=dev")).isFalse();

        System.setProperty("money.workspace.embedded", "true");
        assertThat(shouldStartEmbeddedWorkspace()).isTrue();

        System.clearProperty("money.workspace.embedded");
        assertThat(shouldStartEmbeddedWorkspace("--app.home=C:\\MoneyPOS")).isTrue();
    }

    @Test
    void configuredDataDirectoryOwnsAllRuntimeDirectories(@TempDir Path temporaryDirectory) throws Exception {
        System.setProperty("app.data", temporaryDirectory.toString());
        setStaticField(RuntimeWorkspace.class, "appData", null);
        setStaticField(RuntimeWorkspace.class, "appHome", null);

        RuntimeWorkspace.prepareDirectories();

        assertThat(Path.of(RuntimeWorkspace.getAppData())).isEqualTo(temporaryDirectory.toAbsolutePath());
        assertThat(temporaryDirectory.resolve("assets")).isDirectory();
        assertThat(temporaryDirectory.resolve("logs")).isDirectory();
        assertThat(temporaryDirectory.resolve("backups")).isDirectory();
        assertThat(temporaryDirectory.resolve("db_data")).isDirectory();
        assertThat(System.getProperty("app.data")).isEqualTo(RuntimeWorkspace.getAppData());
        assertThat(System.getProperty("app.home")).isEqualTo(RuntimeWorkspace.getAppHome());
    }

    @Test
    void legacyWorkspaceFacadeDelegatesToTheRuntimeBoundary(@TempDir Path temporaryDirectory) throws Exception {
        System.setProperty("app.data", temporaryDirectory.toString());
        setStaticField(RuntimeWorkspace.class, "appData", null);

        WorkspaceEnv.prepareDirectories();

        assertThat(WorkspaceEnv.getAppData()).isEqualTo(RuntimeWorkspace.getAppData());
        assertThat(temporaryDirectory.resolve("assets")).isDirectory();
    }

    @Test
    void injectorPublishesEmbeddedDatabaseAndAssetContracts(@TempDir Path temporaryDirectory) throws Exception {
        System.setProperty("app.data", temporaryDirectory.toString());
        setStaticField(RuntimeWorkspace.class, "appData", null);
        setStaticField(MariaDbGuardian.class, "dbPassword", "characterized-password");

        RuntimeWorkspaceConfiguration.inject();

        assertThat(System.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://127.0.0.1:9102/money_pos?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8&createDatabaseIfNotExist=true");
        assertThat(System.getProperty("spring.datasource.username")).isEqualTo("root");
        assertThat(System.getProperty("spring.datasource.password")).isEqualTo("characterized-password");
        assertThat(System.getProperty("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(System.getProperty("local.bucket")).isEqualTo(RuntimeWorkspace.getAppData() + "/assets/");
        assertThat(System.getProperty("money.cache.local.provider")).isEqualTo("hutool");
    }

    @Test
    void guardianKeepsItsExistingDatabaseIdentityContract() {
        assertThat(MariaDbGuardian.DB_PORT).isEqualTo(9102);
        assertThat(MariaDbGuardian.DB_NAME).isEqualTo("money_pos");
    }

    private boolean shouldStartEmbeddedWorkspace(String... args) throws Exception {
        Method method = QkMoneyApplication.class.getDeclaredMethod("shouldStartEmbeddedWorkspace", String[].class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, (Object) args);
    }

    private Map<String, String> captureProperties() {
        Map<String, String> values = new HashMap<>();
        for (String property : MANAGED_PROPERTIES) {
            values.put(property, System.getProperty(property));
        }
        return values;
    }

    private void restoreProperties() {
        originalProperties.forEach((property, value) -> {
            if (value == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, value);
            }
        });
    }

    private void setStaticField(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
