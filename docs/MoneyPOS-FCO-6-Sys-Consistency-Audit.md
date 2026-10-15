# MoneyPOS FCO-6：SYS 横切一致性审查记录

> 状态：**只读审查完成，未实施优化**；审查日期：2026-10-15；适用分支：`dev`。

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
| SYS-2 | 高 | `SysStrategyController`、`SysPrintConfigController`、`SysBackupController` 的配置/备份/还原写操作没有 `@PreAuthorize`；`SecurityConfig` 还显式匿名放行备份 SSE。对应的策略、打印、备份、客显菜单均没有权限编码。 | 已认证低权限用户可改全局分析阈值、钱箱/打印行为或触发高影响恢复；匿名连接可接收灾备过程日志。由于没有可复用权限码，不能把任一既有业务权限擅自映射到这些系统能力。 |
| SYS-3 | 中 | `SysStrategyService` 声明“唯一全局策略”，但表仅有自增主键；`SysStrategyMapper.getGlobalStrategy()` 使用无排序的 `SELECT ... LIMIT 1`。 | 若历史或并发写入产生多行，GMS/FIN 可能读取非确定的策略记录。当前初始化数据是一行，尚未发现生产重复数据。 |

## 最小优化候选

**SYS-OPT-1：撤销两条匿名写白名单。** 仅从 `money.security.ignore` 删除 `POST /common/upload` 和
`PUT /common/display-settings`，保留匿名 `GET /common/display-settings`。写接口随安全链恢复为“必须认证”，不新增权限、
菜单、迁移或角色授权，不改客显读取、文件格式、响应、策略/打印/备份流程。验收应包含安全链/配置契约，证明匿名读取仍可用，
匿名写被拒绝且认证写的现有行为不变。

## 需先决策的事项

- **SYS-2 的细粒度授权**：需明确策略、打印、客显写入、备份导出、备份还原和 SSE 日志各自允许的角色，并同时冻结权限码、
迁移、存量角色授予和 Electron/SSE 的认证方案；不能以 `dict:*`、`omsOrder:*` 等不相关权限替代。
- **SYS-3 的唯一策略修复**：需要先盘点真实数据库中的重复行并确认以何行作为权威记录，再决定唯一约束、迁移和并发写入语义；
不能在本轮无数据决策的情况下添加约束或删除记录。
