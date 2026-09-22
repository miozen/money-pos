# AD-6.1：主 reactor Java 17 构建基线实施

## 实施结果

主 Maven reactor 已统一到 Java 17 构建基线，未升级 Spring Boot、未迁移 `javax`/`jakarta`、未改业务、
数据库/Flyway、Electron、MariaDB 或 Maven 物理模块。

| 项目 | 实施结果 |
| --- | --- |
| 编译契约 | 根 `pom.xml` 将 `java.version` 改为 `17`，并以 Maven Compiler Plugin `3.13.0` 的 `release=17` 编译。 |
| 最低 JDK | 根 reactor 的 Maven Enforcer 规则要求 Java 版本为 `[17,)`；低版本 JDK 在构建前失败。 |
| 测试插件 | 保留既有 Surefire `2.22.2`。尝试 `3.2.5` 时改变测试执行条件而暴露两份缺少鉴权夹具的 Mapper 测试；该插件升级不是本任务所需，已撤回。 |
| 测试夹具 | `GmsGoodsMapperIntegrationTest` 和 `GmsBrandMapperIntegrationTest` 补齐与同类 Mapper 测试一致的 `SecurityContext` 设置/清理，修复审计字段填充前的空指针；未改变生产鉴权或业务语义。 |
| 发布工作流 | 保持现有 `build-exe.yml` 的 Temurin 17 和唯一随包 JRE；不下载第二套 JDK。 |

## 本地验收

- 在 Java `17.0.20`、Maven `3.8.7` 下执行隔离 `money_pos_test`：58 个测试类、105 个测试，0 failures、0 errors、0 skipped。
- `mvn -q -DskipTests package` 通过；fat JAR 的 `QkMoneyApplication.class` 为 major version **61**（Java 17）。
- `bash scripts/test-architecture-scan.sh` 与 `bash scripts/architecture-scan.sh --check-new` 通过。
- `git diff --check` 通过。

## 待环境验收

本地实现已完成，但 AD-6.1 仍需要在本提交推送后完成两项外部证据：

1. 手动触发 GitHub `Build WanXiangPOS EXE Installer`，确认 Windows runner 的 Java 17 打包成功；
2. 用该安装包对既有数据目录进行一次打开、`/actuator/health` 和收银窗口人工复验。

这两项仅确认构建基线没有破坏既有发布路径，不重新开启启动性能优化或 ENV-1 的首次初始化/端口冲突/关闭验收。

## 回滚

回滚仅恢复根 `pom.xml` 的 Java/插件治理，以及两份测试夹具；不触及数据库、应用数据目录、安装目录、
业务源码或 Electron 打包资源。
