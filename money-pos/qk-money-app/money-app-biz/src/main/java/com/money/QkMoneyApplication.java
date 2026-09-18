package com.money;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

/**
 * MoneyPOS 系统主启动类
 * 负责引导 Spring Boot 应用及初始化单机版底层环境变量
 */
@SpringBootApplication
public class QkMoneyApplication {

    public static void main(String[] args) {
        // 打包版由 Electron 传入 --app.home，使用随安装包发布的 Windows MariaDB。
        // IDE / WSL 开发模式则连接 application-dev.yml 中配置的外部 MariaDB，
        // 避免在 Linux 下错误地查找 mysqld.exe。
        if (shouldStartEmbeddedWorkspace(args)) {
            com.money.workspace.AppWorkspace.init();
        }

        // 2. 执行 Spring Boot 标准启动逻辑
        SpringApplication.run(QkMoneyApplication.class, args);
    }

    private static boolean shouldStartEmbeddedWorkspace(String[] args) {
        return Boolean.getBoolean("money.workspace.embedded")
                || Arrays.stream(args).anyMatch(arg -> arg.startsWith("--app.home="));
    }

}
