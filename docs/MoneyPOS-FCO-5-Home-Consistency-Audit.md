# MoneyPOS FCO-5：HOME 一致性审查记录

> 状态：**审查完成；HOME-OPT-1 已完成**；审查日期：2026-10-15；适用分支：`dev`。

## 现状地图

HOME 是首页读模型与日经营快照的所有者。HTTP 入口只有
`HomeController.GET /home/count` 与 `GET /home/charts`：前者经 `DecisionEngineService` 读取
`HomeDashboardQueryService`，后者经 `HomeServiceImpl` 组合图表。两个 GET 均不调用快照命令或 Mapper 写操作。

`oms_daily_summary` 是 HOME 自有日快照表；写入路径限定为
`HomeDailySnapshotRefreshTask`（应用就绪一次、其后每五分钟 fixed-delay）→
`HomeDailySnapshotCommandService` → `HomeDailySnapshotAssembler` →
`HomeDailySummaryWriter`。补齐仅覆盖前七个缺失自然日，当日每次重算并通过 `record_date` 唯一键的
`INSERT ... ON DUPLICATE KEY UPDATE` 写入。写入在短事务内完成；组装失败时不进入写入，任务记录错误并保留上一次有效快照。

HOME 的跨 Feature 数据均来自 `money-app-api` 的 Entity-free 查询契约：GMS 的库存估值、UMS 的新增会员及会员分布、
TRADE 的订单汇总/趋势/品牌销售与非商品收款。HOME 不写订单、库存、会员、收款或上游业务表。

`HomeCountSnapshotCharacterizationTest` 覆盖 GET 只读、启动/定时刷新、七日补齐、日快照公式、订单状态口径、非商品收款合并及
today/month/year/total 趋势粒度；`HomeDailySnapshotRefreshTaskTest` 覆盖同 JVM 的重叠跳过和失败后释放守卫。

## 已确认约束

1. `/home/count` 与 `/home/charts` 的公开路径、既有响应字段、统计口径和已完成的浏览器验收保持不变；GET 不得重新获得写快照权限。
2. HOME 仅拥有 `oms_daily_summary` 的读写；订单、非商品收款、库存估值和会员数据继续只经已有 API 契约读取。
3. 当前产品是单机桌面部署；`AtomicBoolean` 只保证单 JVM 调度不重叠。若未来引入多实例运行，必须先冻结跨实例调度/锁策略，不能把该部署变化混入 HOME 小优化。
4. `/home` 路由受安全链的“已认证”要求保护，但没有 HOME 专用菜单/权限资源或已冻结的角色矩阵；不能仅凭控制器没有 `@PreAuthorize` 推断为权限缺陷。

## 已确认问题

| 编号 | 影响 | 证据 | 风险 |
| --- | --- | --- | --- |
| HOME-1 | 低 | `HomeController.GET /home/charts` 接受任意字符串；`HomeServiceImpl.getChartsData` 仅识别 `today/month/year/total`。其他值会保留空的起止时间，而默认日粒度的补全逻辑随后访问空 `trendStartTime`。 | 认证用户以非法 `timeRange` 调用可得到未受控的服务端异常，而非明确的参数错误；该问题不影响浏览器已使用的四个合法值。 |

## 最小优化候选

**HOME-OPT-1：图表时间范围白名单校验。** 在不改变四个合法值、路由、成功响应和趋势/金额公式的前提下，明确只接受
`today`、`month`、`year`、`total`，并让非法值走项目既有的参数错误响应；补充控制器/服务契约测试。该切片不触及快照调度、
权限模型、迁移、历史数据或跨 Feature 契约。

## 非问题与延后事项

- `HomeController` 未使用细粒度权限注解是可见事实，但当前没有可复用的 HOME 权限编码，也没有“哪些已认证角色不得查看首页”的业务规则；将其列为后续 SYS 横切权限复核项，而非本阶段缺陷。
- 日快照表保留 `member_recharge` 字段，但当前 HOME 读模型和对外响应均不消费该字段，刷新 SQL 也刻意不覆盖其既有值。是否把会员充值纳入首页快照属于指标口径变更，不能作为维护型小优化擅自实现。
- 多实例任务互斥不是当前单机部署的缺陷；未来部署模型改变后再单独设计数据库锁或调度平台方案。

## 实施结果

HOME-OPT-1 已按 `MoneyPOS-HOME-OPT-1-Chart-Time-Range-Validation-Implementation-Contract.md` 完成。
`HomeServiceImpl` 在任何查询前仅接受 `today`、`month`、`year`、`total`，其他值以项目既有
`BaseException` 语义失败；新增 HOME 集成回归锁定该边界。合法范围的路径、响应、趋势粒度、金额公式、快照任务、权限、
迁移和跨 Feature 契约均未改变。
