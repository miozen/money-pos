package com.money.platform.runtime.file;

import com.money.platform.runtime.workspace.RuntimeWorkspace;

import java.io.File;

/**
 * Resolves directories owned by the local desktop runtime.
 *
 * <p>Business services may use the returned directories but must not recreate
 * the application-data root or its standard child-directory names.</p>
 */
public final class RuntimeFileStorage {

    private RuntimeFileStorage() {
    }

    public static File assetsDirectory() {
        return dataDirectory("assets");
    }

    public static File logsDirectory() {
        return dataDirectory("logs");
    }

    public static File backupsDirectory() {
        return dataDirectory("backups");
    }

    public static File databaseDirectory() {
        return dataDirectory("db_data");
    }

    public static File dataFile(String filename) {
        return new File(RuntimeWorkspace.getAppData(), filename);
    }

    /** Physical directory form retained for the existing {@code local.bucket} contract. */
    public static String assetsDirectoryPath() {
        return assetsDirectory().getAbsolutePath() + File.separator;
    }

    private static File dataDirectory(String directoryName) {
        return new File(RuntimeWorkspace.getAppData(), directoryName);
    }
}
