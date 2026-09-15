package com.money.platform.runtime.workspace;

import com.money.workspace.MariaDbGuardian;
import lombok.extern.slf4j.Slf4j;

/** Publishes desktop runtime settings for Spring before the application context starts. */
@Slf4j
public final class RuntimeWorkspaceConfiguration {

    private RuntimeWorkspaceConfiguration() {
    }

    public static void inject() {
        String dbUrl = String.format(
                "jdbc:mysql://127.0.0.1:%d/%s?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%%2B8&createDatabaseIfNotExist=true",
                MariaDbGuardian.DB_PORT, MariaDbGuardian.DB_NAME);

        System.setProperty("spring.datasource.url", dbUrl);
        System.setProperty("spring.datasource.username", "root");
        System.setProperty("spring.datasource.password", MariaDbGuardian.getDbPassword());
        System.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        System.setProperty("local.bucket", RuntimeWorkspace.getAppData() + "/assets/");
        System.setProperty("money.cache.local.provider", "hutool");

        log.info("💉 [Injector] 数据库挂载点与静态资源隧道 (Assets) 注入完毕！");
    }
}
