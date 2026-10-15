# MoneyPOS 发行版本统一契约

## 状态

2026-10-13 经用户确认，本文是 MoneyPOS 后续发行版本的冻结契约。它只定义版本标识、构建传播和展示边界；不改变业务数据、接口业务语义、数据库迁移或交易事务。

## 唯一发行来源

`money-pos-web/package.json` 的 `version` 是唯一由发布者维护的发行版本号，必须为 SemVer，示例为 `1.0.12`。发行版本不带展示前缀；界面显示时统一加 `v`。

根 Maven `revision` 的默认值 `0.0.0-DEV` 仅用于未走发行流水线的本地开发构建，不能作为交付版本，也不得手工升级为发行版本。

## 构建传播链

Windows EXE 工作流必须从 `money-pos-web/package.json` 读取并校验 SemVer，然后：

1. 将该值作为 Maven `-Drevision` 构建后端；Spring Boot `build-info` 将其写入 JAR 的 `META-INF/build-info.properties`。
2. 由 Electron Builder 读取同一 `package.json` 生成安装包版本。
3. 由 Vite 在前端构建时注入同一版本，因此 POS 与后台管理界面显示相同版本。
4. 后端版本查询和备份 Manifest 从 JAR 内 `BuildProperties` 读取，不保留独立硬编码版本。

任何绕过该工作流交付的包都不是受支持发行包。若本地调试需要构建后端，版本必须明确显示为 `0.0.0-DEV`，不得伪装为发行版本。

### 本地浏览器联调标准命令

本地联调后端时，以下两步是标准命令；每次后端源码变更后均先重新构建，再启动新 JAR：

```bash
mvn clean package -DskipTests
java -jar qk-money-app/money-app-biz/target/money-app-biz-0.0.0-DEV.jar
```

`-DskipTests` 只用于快速联调，提交或发行前仍须运行既定隔离测试。不得在本地联调命令中手工传入
`-Drevision=1.0.0`（或其他发行版本）；发行版本只由本契约定义的 CI 工作流从
`money-pos-web/package.json` 注入。

## 展示与查询契约

- POS 始终在顶部产品副标题显示 `v{version}`，不干扰扫码、收款或收银员切换。
- 后台管理始终在公共页脚显示 `系统版本 v{version}`。
- `GET /system/version` 返回 `{ "version": "x.y.z" }`，只允许认证用户访问；它只报告后端 JAR 的构建版本，不能作为匿名 Actuator 的替代品。
- 备份 `backup-manifest.json` 的 `appVersion` 必须等于创建备份的后端 JAR 构建版本。跨版本还原仍保留既有告警和兼容尝试行为。

## 发布与验收

每次发行仅修改 `money-pos-web/package.json` 的 `version`，然后由 Windows EXE 工作流生成制品。发布验收必须确认：安装包、POS、后台页脚、`/system/version` 和新备份 Manifest 的版本一致。CI 必须在 Maven 构建前校验版本格式，并在前端构建前运行 `npm run release:check`。
