package com.money.task;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.money.platform.runtime.file.RuntimeFileStorage;
import com.money.service.SysBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * 工业级夜间自动灾备巡航任务
 */
@Slf4j
@Component
@EnableScheduling // 开启 Spring 定时任务支持
@RequiredArgsConstructor
public class SysBackupTask {

    private final SysBackupService sysBackupService;

    /**
     * 🌟 每天凌晨 21:00 准时触发自动全量热备
     * cron 表达式解析：秒 分 时 日 月 星期
     */
    @Scheduled(cron = "0 0 21 * * ?")
    public void executeNightlyBackup() {
        log.info("⏰ [夜间巡航] 开始执行系统级每日自动灾备...");
        try {
            // 1. 调用底层引擎生成备份，前缀加上 AutoBackup_ 以区分手动备份
            File zipFile = sysBackupService.createBackupZip("AutoBackup_");
            log.info("✅ [夜间巡航] 自动灾备完美收官！归档文件位于: {}", zipFile.getName());

            // 2. 执行防爆栈清理策略：默默删掉 7 天前的自动备份包，保护磁盘空间
            cleanOldAutoBackups();

        } catch (Exception e) {
            log.error("❌ [夜间巡航] 自动灾备任务遭遇异常", e);
        }
    }

    /**
     * 🧹 磁盘保护策略：仅保留最近 7 天的“自动备份包” (不会删老板的手动备份)
     */
    private void cleanOldAutoBackups() {
        File dir = RuntimeFileStorage.backupsDirectory();
        if (!dir.exists()) return;

        // 计算 7 天前的时间红线
        Date expireLine = DateUtil.offsetDay(new Date(), -7);

        // 筛选出所有以 AutoBackup_ 开头的 zip 包
        List<File> autoBackupFiles = FileUtil.loopFiles(dir, pathname ->
                pathname.getName().startsWith("AutoBackup_") && pathname.getName().endsWith(".zip"));

        for (File file : autoBackupFiles) {
            // 如果文件的最后修改时间早于 7 天前，无情抹杀！
            if (DateUtil.date(file.lastModified()).isBefore(expireLine)) {
                FileUtil.del(file);
                log.info("🧹 [夜间巡航] 磁盘保护触发，已清理过期自动备份: {}", file.getName());
            }
        }
    }
}
