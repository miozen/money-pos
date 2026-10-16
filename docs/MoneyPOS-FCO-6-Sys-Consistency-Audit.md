# MoneyPOS FCO-6：SYS 横切一致性审查记录

> 状态：**审查完成；SYS-OPT-1 已完成**；审查日期：2026-10-15；适用分支：`dev`。

## 现状地图

SYS 横跨 `money-app-system` 与 `money-app-biz`：前者拥有用户、角色、权限、字典、租户和认证；后者拥有全局策略、
打印/钱箱配置、客显配置、备份恢复、文件落盘及运行时工作区。`sys_strategy`、`sys_print_config`、系统字典与
权限树均由 Flyway 初始化。标准用户/角色/权限/字典 CRUD 已通过 `@PreAuthorize` 和角色等级检查保护。

运行期能力由 `RuntimeWorkspace`、嵌入式 MariaDB、`RuntimeFileStorage` 提供；`PaymentDictCheckRunner` 在启动时验证
支付主字典，避免资金名称在缺少权威字典时运行。`SysBackupTask` 每日生成自动备份并仅清理七日前的自动归档。

跨 Feature 读取遵循现有窄契约：GMS 周转通过 `TurnoverStrategyQuery`，FIN 客流分析通过
`FinanceTrafficStrategyQuery`；两者由 SYS 从全局策略投影实现。HOME 不直接依赖 SYS 配置。打印服务读取 SYS 自有
打印配置和字典显示值，不能反转已经成功的结算事务。

现有测试覆盖策略 Mapper/服务、固定 ID 的打印配置，以及运行时数据目录与启动能力；GMS/FIN 集成测试还覆盖策略读侧
契约的消费。备份还原、公开白名单与 SYS 高风险路由尚无专门的 HTTP 授权契约测试。

## 已确认约束

1. 用户、角色、权限、字典、租户继续由 `money-app-system` 所有；策略、打印、客显、备份和运行时能力继续由 SYS 所有。
2. GMS/FIN 只能继续使用既有 `money-app-api` 的策略快照查询契约，不得直连 SYS Mapper 或实体。
3. 客显设备需要匿名读取 `/common/display-settings`；该读取路径和 JSON 响应不能随安全加固改变。
4. 嵌入式数据库、备份恢复、打印/钱箱与 Windows 运行行为已冻结；任何改变其流程、历史数据或部署模型的方案必须另行冻结。

## 已确认问题

| 编号 | 影响 | 证据 | 风险 |
| --- | --- | --- | --- |
| SYS-1 | 高 | `application-money.yml` 将 `POST /common/upload` 与 `PUT /common/display-settings` 加入 security ignore；`CommonController` 分别写本地资产和 `SYSTEM_SETTING/customer_display`。 | 未认证调用者可上传文件或篡改客显文案、支付码与营销阈值；前端菜单隐藏不能替代服务端授权。 |
| SYS-2 | 高（已部分决策） | 日常 SYS 配置路由未使用 `@PreAuthorize`，`SysBackupController` 的导出/还原原先也未使用角色保护；`SecurityConfig` 仍显式匿名放行备份 SSE。对应菜单没有细粒度权限编码。 | 用户已确认日常 SYS 配置允许所有已认证用户，故不是缺陷；备份导出/还原必须仅限超级管理员。匿名 SSE 日志的认证与 Electron 兼容方式尚未决策。 |
| SYS-3 | 中 | `SysStrategyService` 声明“唯一全局策略”，但表仅有自增主键；`SysStrategyMapper.getGlobalStrategy()` 使用无排序的 `SELECT ... LIMIT 1`。 | 若历史或并发写入产生多行，GMS/FIN 可能读取非确定的策略记录。当前初始化数据是一行，尚未发现生产重复数据。 |

## 最小优化候选

**SYS-OPT-1：撤销两条匿名写白名单。** 仅从 `money.security.ignore` 删除 `POST /common/upload` 和
`PUT /common/display-settings`，保留匿名 `GET /common/display-settings`。写接口随安全链恢复为“必须认证”，不新增权限、
菜单、迁移或角色授权，不改客显读取、文件格式、响应、策略/打印/备份流程。验收应包含安全链/配置契约，证明匿名读取仍可用，
匿名写被拒绝且认证写的现有行为不变。

## 已确认的授权决策

- 策略、打印、客显及其他日常 SYS 配置：所有已认证用户可操作，不引入细粒度权限设计。
- 备份导出与还原：仅既有 `SUPER_ADMIN` 角色可操作，按
  `MoneyPOS-SYS-OPT-2-Backup-Administrator-Guard-Implementation-Contract.md` 实施。

## 需先决策的事项

- **备份 SSE 日志认证**：现有 `/sys/backup/stream` 显式匿名放行。其是否应认证及 Electron/SSE 如何携带认证信息仍需单独确认；
  在没有兼容方案前，不在导出/还原权限切片中隐式修改。
- **SYS-3 的唯一策略修复**：需要先盘点真实数据库中的重复行并确认以何行作为权威记录，再决定唯一约束、迁移和并发写入语义；
不能在本轮无数据决策的情况下添加约束或删除记录。

## 实施结果

SYS-OPT-1 已按 `MoneyPOS-SYS-OPT-1-Anonymous-Write-Whitelist-Removal-Implementation-Contract.md` 完成：
`POST /common/upload` 与 `PUT /common/display-settings` 已移出匿名白名单，默认安全链要求认证；
`GET /common/display-settings` 仍保持匿名读取。控制器、路径、请求/响应、文件格式、客显 JSON、权限码、角色授权、迁移及
SYS-2/SYS-3 的待决事项均未改变。

SYS-OPT-2 已按 `MoneyPOS-SYS-OPT-2-Backup-Administrator-Guard-Implementation-Contract.md` 完成：
`GET /sys/backup/export` 与 `POST /sys/backup/restore` 现仅允许既有 `SUPER_ADMIN` 角色。备份 ZIP、流式下载、还原流程、
响应和既有匿名 SSE 日志通道均未改变；SSE 认证仍为待决兼容性事项。

## 审查后最小修复

SYS-FIX-1 已按 `MoneyPOS-SYS-FIX-1-Dict-Detail-Sort-Persistence-Implementation-Contract.md` 完成。确认原
`SysDictDetailDTO` 遗漏 `sort`，使既有对象复制不能写入用户提交的排序，数据库默认值 `999` 因而掩盖了该问题。仅补齐
DTO 字段，复用既有服务与 `sort ASC` 读取逻辑；新增、修改和读取顺序已有隔离集成回归覆盖。未改变字典路由、权限、响应、
数据库默认值或历史数据。

SYS-FIX-2 已按 `MoneyPOS-SYS-FIX-2-Backup-Console-Log-Reliability-Implementation-Contract.md` 完成。修复范围只覆盖
备份控制台可观测性：SSE 与 Axios 共用 Electron/浏览器运行时服务地址，日志 payload 由 JSON 安全序列化，手动备份和
还原前置快照会发布明确的阶段进度。导出/还原/SSE 路径、ZIP、影子库与资源切换、`SUPER_ADMIN` 导出/还原限制、匿名 SSE
兼容性和定时自动备份行为均保持不变。

SYS-FIX-3 已按 `MoneyPOS-SYS-FIX-3-Tenant-Logo-Upload-Compatibility-Implementation-Contract.md` 完成。确认问题位于
前端资源地址兼容层：`blob:` 预览地址会被错误拼入 assets 路径，Electron `file://` 包又不会沿用 Axios 的本地 Java
服务基址。现已统一运行时基址并透传本地/外部 URL；租户 Controller 同时显式固定既有 `logo` multipart part，并由 MVC
回归覆盖。租户数据、存储目录、路由、权限、文件格式、Logo 删除时机和多租户规则均未改变。
