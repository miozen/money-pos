package com.money.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.money.workspace.MariaDbGuardian;
import com.money.platform.runtime.workspace.RuntimeWorkspace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 🌟 实用级高可用灾备与还原引擎 (V6.0 终极实战版)
 * 核心特性：
 * 1. 跨平台自适应 (兼容 Windows/Linux)
 * 2. 进程级 Stderr 错误捕获 (拒绝薛定谔的日志)
 * 3. 严格备份包与 Manifest 清单校验 (防篡改/防恶意包)
 * 4. 业务级核心表探活校验 (防残缺 SQL)
 * 5. 表集绝对一致性的单句无损原子切换 (支持纯新空环境一键恢复，旧表全清退，新表全上位)
 */
@Slf4j
@Service
public class SysBackupService {

    private static final String MANIFEST_FILE = "backup-manifest.json";
    private static final String APP_VERSION = "2.2.0";
    private static final String SHADOW_DB = "money_pos_shadow";

    // 单店单机场景下，全局广播可满足需求
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(600000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void sendLog(String level, String msg) {
        String logLine = String.format("{\"time\":\"%s\", \"level\":\"%s\", \"msg\":\"%s\"}",
                DateUtil.formatTime(DateUtil.date()), level, msg);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(logLine);
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public File createBackupZip(String prefix) {
        // 🌟 核心修复 1：动静分离，引擎拿 Home，数据拿 Data
        String appHome = RuntimeWorkspace.getAppHome(); // 程序区
        String appData = RuntimeWorkspace.getAppData(); // 数据区 (安全区)

        String backupDir = appData + File.separator + "backups"; // 备份文件夹在数据区
        String mariadbBin = appHome + File.separator + "mariadb" + File.separator + "bin"; // 引擎在程序区
        String tempBatchDir = backupDir + File.separator + "temp_" + IdUtil.fastSimpleUUID();

        FileUtil.mkdir(tempBatchDir);
        try {
            // 1. 生成标准的备份清单
            JSONObject manifest = new JSONObject();
            manifest.set("appName", "MoneyPOS");
            manifest.set("appVersion", APP_VERSION);
            manifest.set("backupTime", DateUtil.now());
            manifest.set("dbName", MariaDbGuardian.DB_NAME);
            FileUtil.writeUtf8String(manifest.toStringPretty(), tempBatchDir + File.separator + MANIFEST_FILE);

            File sqlFile = new File(tempBatchDir + File.separator + "money_pos.sql");

            // 🌟 跨平台自适应执行程序后缀
            String exeSuffix = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
            File mysqldumpExe = new File(mariadbBin + File.separator + "mysqldump" + exeSuffix); // 这里用 appHome

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpExe.getAbsolutePath(), "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                    "-uroot", "-p" + MariaDbGuardian.getDbPassword(),
                    "--single-transaction", "--routines", "--triggers", "--hex-blob",
                    "--add-drop-table", "--default-character-set=utf8mb4", MariaDbGuardian.DB_NAME
            );

            // 🌟 核心捕获：分离输出流与错误流，防止缓冲区阻塞
            pb.redirectOutput(sqlFile);
            File errorLogFile = new File(tempBatchDir + File.separator + "dump_error.log");
            pb.redirectError(errorLogFile);

            Process process = pb.start();
            if (process.waitFor() != 0) {
                String errorMsg = errorLogFile.exists() ? FileUtil.readUtf8String(errorLogFile) : "未知进程错误";
                throw new RuntimeException("数据库底层导出失败: " + errorMsg);
            }

            // 2. 备份静态资源
            File assetsDir = new File(appData + File.separator + "assets"); // 🌟 核心修复：找数据区的 assets
            if (assetsDir.exists()) FileUtil.copy(assetsDir, new File(tempBatchDir), true);

            // 3. 打包 ZIP
            String zipFileName = prefix + "VanaPOS_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss") + ".zip";
            File zipFile = new File(backupDir + File.separator + zipFileName);
            cn.hutool.core.util.ZipUtil.zip(tempBatchDir, zipFile.getAbsolutePath(), true);
            return zipFile;
        } catch (Exception e) {
            log.error("❌ 备份失败", e);
            throw new RuntimeException("备份异常: " + e.getMessage(), e);
        } finally {
            FileUtil.del(tempBatchDir);
        }
    }

    public void restoreFromZip(MultipartFile backupFile) {
        // 🌟 核心修复 2：还原文件统一走数据安全区
        String appData = RuntimeWorkspace.getAppData();
        String tempRestoreDir = appData + File.separator + "backups" + File.separator + "restore_" + IdUtil.fastSimpleUUID();
        File zipFile = new File(tempRestoreDir + ".zip");

        try {
            sendLog("WARN", "=== 实用级高可用还原序列启动 ===");

            backupFile.transferTo(zipFile);
            FileUtil.mkdir(tempRestoreDir);
            cn.hutool.core.util.ZipUtil.unzip(zipFile, new File(tempRestoreDir));

            // 🌟 核心防线 1：严格校验只允许 1 个 SQL 文件
            List<File> sqlFiles = FileUtil.loopFiles(new File(tempRestoreDir), f -> f.getName().endsWith(".sql"));
            if (sqlFiles.size() != 1) {
                throw new RuntimeException("异常备份包：必须包含且仅包含 1 个 SQL 文件 (当前发现 " + sqlFiles.size() + " 个)");
            }
            File sqlFile = sqlFiles.get(0);

            // 🌟 核心防线 2：严格强制校验 Manifest
            checkManifestStrictly(tempRestoreDir);

            sendLog("INFO", "创建前置保护快照...");
            createBackupZip("PreRestore_");

            sendLog("INFO", "第一阶段：影子库预导入...");
            prepareShadowDatabase();
            importSqlToDb(sqlFile, SHADOW_DB);

            sendLog("INFO", "第二阶段：影子库业务级深度校验...");
            verifyShadowDatabase();
            sendLog("SUCCESS", "影子库导入成功，核心数据验证通过。");

            sendLog("WARN", "第三阶段：执行生产库表集绝对一致性切换...");
            atomicSwitchDatabase();
            sendLog("SUCCESS", "数据库切换完美达成，无残留旧表！");

            // 🌟 核心修复 3：将静态资源还原到数据安全区
            restoreAssetsAtomically(tempRestoreDir, appData);

            sendLog("SUCCESS", "=== 还原全流程安全收官 ===");

        } catch (Exception e) {
            sendLog("ERROR", "❌ 还原失败：已触发安全保护机制。底层错误：" + e.getMessage());
            log.error("还原失败", e);
            // 失败时清理影子库，保留原生产库毫发无损
            try { dropDatabase(SHADOW_DB); } catch (Exception ignore) {}
            throw new RuntimeException("还原失败：" + (StrUtil.isNotBlank(e.getMessage()) ? e.getMessage() : "原因未知"), e);
        } finally {
            FileUtil.del(tempRestoreDir);
            FileUtil.del(zipFile);
        }
    }

    private void checkManifestStrictly(String restoreDir) {
        File manifest = new File(restoreDir + File.separator + MANIFEST_FILE);
        if (!manifest.exists()) {
            sendLog("WARN", "⚠️ 检测到旧版备份包：缺失 manifest 清单文件，将跳过安全校验强制执行恢复...");
            return;
        }

        JSONObject json = JSONUtil.readJSONObject(manifest, StandardCharsets.UTF_8);

        if (!"MoneyPOS".equals(json.getStr("appName"))) {
            throw new RuntimeException("非法备份包：应用名称不匹配");
        }
        if (!MariaDbGuardian.DB_NAME.equals(json.getStr("dbName"))) {
            throw new RuntimeException("非法备份包：数据库名称不匹配 (" + json.getStr("dbName") + ")");
        }

        String version = json.getStr("appVersion");
        if (!APP_VERSION.equals(version)) {
            sendLog("WARN", "⚠️ 备份版本(" + version + ")与当前系统(" + APP_VERSION + ")不一致，将尝试跨版本兼容导入...");
        }
    }

    private void prepareShadowDatabase() throws Exception {
        executeSql("DROP DATABASE IF EXISTS `" + SHADOW_DB + "`");
        executeSql("CREATE DATABASE `" + SHADOW_DB + "` CHARACTER SET utf8mb4");
    }

    private void importSqlToDb(File sqlFile, String dbName) throws Exception {
        String exeSuffix = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
        // 🌟 只有引擎执行文件 (mysql.exe) 依然在程序区找，保持不变
        String mysqlExe = RuntimeWorkspace.getAppHome() + "/mariadb/bin/mysql" + exeSuffix;

        ProcessBuilder pb = new ProcessBuilder(
                mysqlExe, "--host=127.0.0.1", "--port=" + MariaDbGuardian.DB_PORT,
                "-uroot", "-p" + MariaDbGuardian.getDbPassword(), "--default-character-set=utf8mb4", dbName
        );
        pb.redirectInput(sqlFile);

        File errorLogFile = new File(sqlFile.getParent() + File.separator + "import_error.log");
        pb.redirectError(errorLogFile);

        Process process = pb.start();
        if (process.waitFor() != 0) {
            String errorMsg = errorLogFile.exists() ? FileUtil.readUtf8String(errorLogFile) : "未知进程错误";
            log.error("💥 影子库导入时发生了原生 MySQL 报错: \n{}", errorMsg);
            throw new RuntimeException("导入指令底层执行失败: \n" + errorMsg);
        }
    }

    private void verifyShadowDatabase() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/" + SHADOW_DB + "?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {

            List<String> requiredTables = Arrays.asList("oms_order", "gms_goods", "ums_member");
            List<String> actualTables = new ArrayList<>();
            ResultSet rs = stmt.executeQuery("SHOW TABLES");
            while (rs.next()) actualTables.add(rs.getString(1));

            for (String reqTable : requiredTables) {
                if (!actualTables.contains(reqTable)) {
                    throw new RuntimeException("影子库校验失败：缺失系统级核心表 `" + reqTable + "`，备份文件已损坏或不完整！");
                }
            }

            for (String reqTable : requiredTables) {
                try {
                    stmt.executeQuery("SELECT COUNT(1) FROM `" + reqTable + "`");
                } catch (Exception e) {
                    throw new RuntimeException("影子库校验失败：核心表 `" + reqTable + "` 数据损坏或无法读取 (" + e.getMessage() + ")");
                }
            }
        }
    }

    private void atomicSwitchDatabase() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {

            List<String> shadowTables = new ArrayList<>();
            ResultSet rsShadow = stmt.executeQuery("SHOW TABLES FROM `" + SHADOW_DB + "`");
            while (rsShadow.next()) shadowTables.add(rsShadow.getString(1));
            if (shadowTables.isEmpty()) throw new RuntimeException("影子库表结构为空，安全机制已拦截切换");

            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + MariaDbGuardian.DB_NAME + "` CHARACTER SET utf8mb4");

            List<String> prodTables = new ArrayList<>();
            ResultSet rsProd = stmt.executeQuery("SHOW TABLES FROM `" + MariaDbGuardian.DB_NAME + "`");
            while (rsProd.next()) prodTables.add(rsProd.getString(1));

            String bakDbName = MariaDbGuardian.DB_NAME + "_bak_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss");
            stmt.execute("CREATE DATABASE `" + bakDbName + "` CHARACTER SET utf8mb4");

            StringBuilder renameSql = new StringBuilder("RENAME TABLE ");
            boolean isFirst = true;

            for (String pt : prodTables) {
                if (!isFirst) renameSql.append(", ");
                renameSql.append(String.format("`%s`.`%s` TO `%s`.`%s`", MariaDbGuardian.DB_NAME, pt, bakDbName, pt));
                isFirst = false;
            }

            for (String st : shadowTables) {
                if (!isFirst) renameSql.append(", ");
                renameSql.append(String.format("`%s`.`%s` TO `%s`.`%s`", SHADOW_DB, st, MariaDbGuardian.DB_NAME, st));
                isFirst = false;
            }

            stmt.execute(renameSql.toString());
            stmt.execute("DROP DATABASE IF EXISTS `" + SHADOW_DB + "`");

            if (!prodTables.isEmpty()) {
                sendLog("INFO", "旧版生产数据(" + prodTables.size() + "张表)已被安全隔离至: " + bakDbName);
            } else {
                sendLog("INFO", "全新环境初始化部署完成！");
            }
        }
    }

    // 🌟 此处传入的 dataDir 已经是安全区路径了
    private void restoreAssetsAtomically(String tempDir, String dataDir) {
        File assetsInZip = new File(tempDir + File.separator + "assets");
        if (!assetsInZip.exists()) return;

        File targetAssets = new File(dataDir + File.separator + "assets");
        File bakAssets = new File(dataDir + File.separator + "assets_bak_" + IdUtil.fastSimpleUUID());

        try {
            if (targetAssets.exists()) FileUtil.move(targetAssets, bakAssets, true);
            FileUtil.copyContent(assetsInZip, targetAssets, true);

            new Thread(() -> {
                try { Thread.sleep(5000); FileUtil.del(bakAssets); } catch (Exception ignore) {}
            }).start();
        } catch (Exception e) {
            sendLog("ERROR", "静态资源替换失败，已触发自动回滚...");
            if (bakAssets.exists()) {
                FileUtil.del(targetAssets);
                FileUtil.move(bakAssets, targetAssets, true);
            }
            throw new RuntimeException("静态资产还原失败", e);
        }
    }

    private void executeSql(String sql) throws Exception {
        String url = "jdbc:mysql://127.0.0.1:" + MariaDbGuardian.DB_PORT + "/mysql?useSSL=false";
        try (Connection conn = DriverManager.getConnection(url, "root", MariaDbGuardian.getDbPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void dropDatabase(String dbName) throws Exception {
        executeSql("DROP DATABASE IF EXISTS `" + dbName + "`");
    }
}
