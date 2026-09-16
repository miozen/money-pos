package com.money.platform.runtime.workspace;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.money.platform.runtime.database.EmbeddedMariaDbGuardian;
import com.money.platform.runtime.file.RuntimeFileStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * Desktop runtime workspace boundary.
 *
 * <p>This is the stable entry point for application and data locations. Business
 * code may read these locations but must not reproduce the desktop path rules.</p>
 */
@Slf4j
public final class RuntimeWorkspace {

    private static String appHome;
    private static String appData;

    private RuntimeWorkspace() {
    }

    /** Initializes the embedded desktop runtime before Spring starts. */
    public static void initialize() {
        prepareDirectories();
        EmbeddedMariaDbGuardian.start();
        RuntimeWorkspaceConfiguration.inject();
    }

    /** Returns the application installation directory. */
    public static String getAppHome() {
        if (appHome != null) {
            return appHome;
        }
        try {
            String path = RuntimeWorkspace.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File file = new File(path);
            appHome = file.isFile()
                    ? file.getParentFile().getAbsolutePath()
                    : file.getParentFile().getParentFile().getAbsolutePath();
        } catch (Exception e) {
            appHome = System.getProperty("user.dir");
        }
        appHome = FileUtil.normalize(appHome);
        return appHome;
    }

    /** Returns the persistent desktop data directory. */
    public static String getAppData() {
        if (appData != null) {
            return appData;
        }

        String configuredData = System.getProperty("app.data");
        if (StrUtil.isNotBlank(configuredData)) {
            appData = FileUtil.normalize(configuredData);
            return appData;
        }

        String[] drives = {"D:\\", "E:\\", "C:\\"};
        for (String drive : drives) {
            if (FileUtil.exist(drive)) {
                String candidate = drive + "WanXiang/POS-Data";
                File metaFile = new File(candidate, ".wx_meta");
                File ibdataFile = new File(candidate, "db_data/ibdata1");
                if (metaFile.exists() && ibdataFile.exists()) {
                    appData = FileUtil.normalize(candidate);
                    return appData;
                }
            }
        }

        appData = FileUtil.exist("D:\\")
                ? FileUtil.normalize("D:\\WanXiang\\POS-Data")
                : FileUtil.normalize("C:\\WanXiang\\POS-Data");
        return appData;
    }

    /** Creates the runtime-owned directory layout and publishes its stable properties. */
    public static void prepareDirectories() {
        String resolvedAppHome = getAppHome();
        String resolvedAppData = getAppData();

        FileUtil.mkdir(RuntimeFileStorage.assetsDirectory());
        FileUtil.mkdir(RuntimeFileStorage.logsDirectory());
        FileUtil.mkdir(RuntimeFileStorage.backupsDirectory());
        FileUtil.mkdir(RuntimeFileStorage.databaseDirectory());

        log.info("⚙️ [Workspace] 程序核心锁定于: {}", resolvedAppHome);
        log.info("🛡️ [Workspace] 数据资产锁定于: {}", resolvedAppData);

        System.setProperty("app.data", resolvedAppData);
        System.setProperty("app.home", resolvedAppHome);
    }
}
