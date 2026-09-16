package com.money.platform.runtime.file;

import com.money.platform.runtime.workspace.RuntimeWorkspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeFileStorageTest {

    @AfterEach
    void restoreWorkspace() throws Exception {
        System.clearProperty("app.data");
        Field appData = RuntimeWorkspace.class.getDeclaredField("appData");
        appData.setAccessible(true);
        appData.set(null, null);
    }

    @Test
    void resolvesAllStandardRuntimeDirectoriesBelowConfiguredDataRoot(@TempDir Path dataRoot) throws Exception {
        System.setProperty("app.data", dataRoot.toString());
        Field appData = RuntimeWorkspace.class.getDeclaredField("appData");
        appData.setAccessible(true);
        appData.set(null, null);

        assertThat(RuntimeFileStorage.assetsDirectory().toPath()).isEqualTo(dataRoot.resolve("assets"));
        assertThat(RuntimeFileStorage.logsDirectory().toPath()).isEqualTo(dataRoot.resolve("logs"));
        assertThat(RuntimeFileStorage.backupsDirectory().toPath()).isEqualTo(dataRoot.resolve("backups"));
        assertThat(RuntimeFileStorage.databaseDirectory().toPath()).isEqualTo(dataRoot.resolve("db_data"));
        assertThat(RuntimeFileStorage.dataFile(".wx_meta").toPath()).isEqualTo(dataRoot.resolve(".wx_meta"));
        assertThat(RuntimeFileStorage.assetsDirectoryPath()).endsWith("assets/");
    }
}
