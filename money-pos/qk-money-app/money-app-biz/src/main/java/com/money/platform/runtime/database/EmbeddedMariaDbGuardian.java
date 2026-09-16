package com.money.platform.runtime.database;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.money.platform.runtime.workspace.RuntimeWorkspace;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/** Owns the MariaDB engine lifecycle bundled with an explicitly enabled desktop package. */
@Slf4j
public final class EmbeddedMariaDbGuardian {

    public static final int DB_PORT = 9102;
    public static final String DB_NAME = "money_pos";

    private static String dbPassword = "";
    private static boolean isFirstRun = false;
    private static Process dbProcess;

    private EmbeddedMariaDbGuardian() {
    }

    public static String getDbPassword() {
        return dbPassword;
    }

    public static void start() {
        prepareSecretKey();
        bootEngine();
    }

    private static void prepareSecretKey() {
        File pwdFile = new File(RuntimeWorkspace.getAppData(), ".sys_secret.key");
        if (pwdFile.exists()) {
            dbPassword = FileUtil.readString(pwdFile, StandardCharsets.UTF_8).trim();
            isFirstRun = false;
        } else {
            dbPassword = "Mp_" + RandomUtil.randomString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 12);
            isFirstRun = true;
        }
    }

    private static void writeMetaLock() {
        File metaFile = new File(RuntimeWorkspace.getAppData(), ".wx_meta");
        if (!metaFile.exists()) {
            String metaContent = "{\n  \"app\": \"WanXiangPOS\",\n  \"version\": \"1.0\",\n  \"createdAt\": \"" + DateUtil.now() + "\"\n}";
            FileUtil.writeString(metaContent, metaFile, StandardCharsets.UTF_8);
            log.info("🔐 [Guardian] 账本防伪基因锁 (.wx_meta) 写入完成。");
        }
    }

    private static void bootEngine() {
        String mariadbEnginePath = RuntimeWorkspace.getAppHome() + "/mariadb";
        File mysqldExe = new File(mariadbEnginePath + "/bin/mysqld.exe");
        File installDbExe = new File(mariadbEnginePath + "/bin/mysql_install_db.exe");
        File dataDir = new File(RuntimeWorkspace.getAppData() + "/db_data");
        String expectedDataDir = FileUtil.normalize(dataDir.getAbsolutePath());

        if (!mysqldExe.exists()) {
            throw new RuntimeException("❌ [Guardian] 致命错误：缺失数据库引擎文件!");
        }
        if (isPortInUse(DB_PORT)) {
            if (verifyInstanceIdentity(expectedDataDir)) {
                log.info("✅ [Guardian] 鉴定成功：该实例为本程序关联账本，直接复用。");
                return;
            }
            throw new RuntimeException("❌ [Guardian] 端口被非关联数据库占用！");
        }

        try {
            if (isFirstRun && (!dataDir.exists() || FileUtil.isEmpty(dataDir))) {
                log.info("⏳ [Guardian] 正在安全区 [{}] 初始化数据库内核...", dataDir.getAbsolutePath());
                Process initProcess = new ProcessBuilder(installDbExe.getAbsolutePath(), "--datadir=" + dataDir.getAbsolutePath())
                        .redirectErrorStream(true).start();
                drainStream(initProcess.getInputStream());
                initProcess.waitFor();
                writeMetaLock();
            }

            log.info("⚙️ [Guardian] 正在拉起私有 MariaDB 服务...");
            dbProcess = new ProcessBuilder(
                    mysqldExe.getAbsolutePath(), "--port=" + DB_PORT,
                    "--datadir=" + dataDir.getAbsolutePath(), "--character-set-server=utf8mb4"
            ).directory(new File(mariadbEnginePath)).redirectErrorStream(true).start();
            drainStream(dbProcess.getInputStream());
            Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedMariaDbGuardian::gracefulShutdown));

            if (!waitForDatabaseReady(expectedDataDir)) {
                throw new RuntimeException("❌ [Guardian] 数据库就绪校验失败！");
            }
        } catch (Exception e) {
            log.error("❌ [Guardian] 守护进程启动失败: ", e);
            System.exit(1);
        }
    }

    private static boolean verifyInstanceIdentity(String expectedDir) {
        String currentPwd = isFirstRun ? "" : dbPassword;
        String url = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", currentPwd);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT @@datadir");
            return !rs.next() || sameDataDirectory(FileUtil.normalize(rs.getString(1)), expectedDir);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean sameDataDirectory(String actualDir, String expectedDir) {
        String normalizedActual = StrUtil.removeSuffix(StrUtil.removeSuffix(actualDir, "/"), "\\");
        String normalizedExpected = StrUtil.removeSuffix(StrUtil.removeSuffix(expectedDir, "/"), "\\");
        return normalizedActual.equalsIgnoreCase(normalizedExpected);
    }

    static boolean isPortInUse(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean waitForDatabaseReady(String expectedDir) {
        String currentPwd = isFirstRun ? "" : dbPassword;
        String sysUrl = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false&serverTimezone=GMT%2B8";
        for (int i = 0; i < 15; i++) {
            try (Connection conn = DriverManager.getConnection(sysUrl, "root", currentPwd)) {
                if (!verifyInstanceIdentity(expectedDir)) return false;
                if (isFirstRun) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + dbPassword + "'");
                        stmt.execute("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` CHARACTER SET utf8mb4");
                        FileUtil.writeString(dbPassword, new File(RuntimeWorkspace.getAppData(), ".sys_secret.key"), StandardCharsets.UTF_8);
                        log.info("🛡️ [Guardian] 数据库内核加固与空库创建完毕。");
                    }
                }
                return true;
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static void gracefulShutdown() {
        log.info("\n🛑 [Guardian] 正在执行优雅停机...");
        String url = "jdbc:mysql://127.0.0.1:" + DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", dbPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
            if (dbProcess != null) dbProcess.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (dbProcess != null) dbProcess.destroyForcibly();
        }
    }

    private static void drainStream(InputStream inputStream) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                while (reader.readLine() != null) {
                    // Drain native process output to prevent the pipe from filling.
                }
            } catch (Exception ignored) {
                // Native process output is diagnostic only.
            }
        }).start();
    }
}
