# MoneyPOS OMNIA-CLOUD-5 外部配置与同步状态实施契约

> 状态：已确认，按本切片实施；确认日期：2026-10-21；适用分支：`dev`。

## 目标与边界

让 Windows Electron 成品可从安装目录之外加载 Omnia Cloud 的受保护运行时配置，并在后台提供仅限超级管理员的只读同步状态。MariaDB 仍是唯一权威数据源；本切片不通过界面保存密钥、不提供启停/重置/补发写操作，也不自动开启真实会员外发。

## 冻结决定

- 成品 Electron 始终以可选 Spring 配置位置加载 `%ProgramData%\\VanaPOS\\config\\omnia-cloud.yml`；缺失该文件时维持现有默认关闭行为。安装包、Git 和 MariaDB 均不保存端点或密钥。
- 外部文件由 Windows 管理员创建，并限制为 `SYSTEM` 与本机 Administrators 可读；文件中可配置现有 `omnia.cloud.sync.*` 属性。示例只给字段名和非生产占位值。
- 后台“Omnia Cloud 同步”仅对 `SUPER_ADMIN` 可见。接口仅返回配置是否完整、启用/首次回填状态、门店/租户标识和本地 Outbox/回填检查点计数；绝不返回 URL、签名密钥、检索 HMAC、原始手机号或失败原文。

## 验收与后续

验收包含：缺失外部文件不影响 Electron 启动；外部文件位置传给 Spring；只读状态接口受专用权限保护且 DTO 不含秘密；前端能构建并显示状态。真实密钥轮换、配置文件 ACL 落地、启用真实同步与 POS 机端验收，均留待本切片完成后的单独运行时步骤。

## 完成事实

- Electron 成品启动时将可选加载 `%ProgramData%\\VanaPOS\\config\\omnia-cloud.yml`；示例文件位于 `money-pos-web/config/omnia-cloud.yml.example`，其中不包含任何生产值。
- `V1.0.25` 新增仅授予 `SUPER_ADMIN` 的状态菜单和 `omniaCloud:view` 读取权限；接口与页面只显示本地待发箱/回填检查点和非秘密配置状态。
- 已在隔离 `money_pos_test` 验证该迁移，`UmsMemberCloudSyncRouteContractTest` 与首次回填集成测试通过；完整 Maven 回归 88 份 Surefire 报告、0 failure/error，Node 20 前端构建、Maven 打包、两项架构门禁与差异检查通过。
