# MoneyPOS AD-1.2：HOME 日快照命令模型与兼容迁移设计

## 决策

将 HOME 日快照写入拆为 HOME Feature 内部的显式命令与只读查询，而不把
`OmsDailySummary` 的写入移交给 TRADE。第一实施阶段以数据库已有的
`uk_record_date(record_date)` 为并发仲裁，通过两种原子写入语义消除“查后插”异常：

1. **历史补偿：仅在缺失时插入，已有行绝不重算。**
2. **当天刷新：按日期原子 upsert，保留当前的覆盖（last writer wins）语义。**

本设计不需要新增表、唯一键、版本列或 Flyway。它不会假装解决跨所有者输入的时间一致性；
订单、会员与库存仍按现有读模型在命令组装时取得。若未来需要真实日终库存或事务事件一致性，
必须另立业务语义任务。

## 目标形态

```text
                 ┌──────────────────────────────────┐
TRADE / UMS / GMS│ Home owner query contracts         │
                 └───────────────┬──────────────────┘
                                 ▼
                  HomeDailySnapshotAssembler
                  （组装数值；不持有写事务）
                                 │ DailySnapshotValues
                                 ▼
                  HomeDailySnapshotCommandService
                    ├─ insertMissing(date)  历史补偿
                    └─ refreshToday(date)   当天覆盖
                                 ▼
                  HOME DailySummaryWriter / Mapper
                                 ▼
                         oms_daily_summary

GET /home/count -> HomeDashboardQueryService -> 只读当天快照、七日均值、告警和区间大盘
```

建议组件全部留在 `feature.home.application` 与 HOME 本域持久化边界；不在
`money-app-api` 新增跨 Feature 命令，因为日快照是 HOME 的读模型写入，不是 TRADE 的
订单写侧职责。

## 命令职责与数据模型

| 组件 | 建议职责 | 禁止承担的责任 |
| --- | --- | --- |
| `HomeDailySnapshotAssembler` | 从 `HomeOrderReadQuery`、`HomeDailyMemberQuery`、`InventoryValuationQuery` 取得现有数值，计算利润/ASP，形成 Java 8 普通不可变 `HomeDailySnapshotValues`。 | 不写数据库、不改日期窗口、不发出跨域写命令。 |
| `HomeDailySnapshotCommandService` | 明确提供 `compensateMissingSnapshots(LocalDate today, int days)` 与 `refreshSnapshot(LocalDate date)`。历史命令只允许过去七日；当天命令允许覆盖。 | 不组装 HTTP 响应或告警 Map。 |
| `HomeDailySummaryWriter` | 在独立 Spring Bean 的短事务内执行单条原子 insert-if-absent 或 upsert。 | 不重新读取 TRADE/UMS/GMS，不使用应用级全局锁。 |
| `HomeDashboardQueryService` | 读取 HOME 快照、过去七日均值并组装 today/month/year/total/alerts。 | 不调用刷新、补偿或任何写命令。 |

`HomeDailySnapshotValues` 只包含现有快照写入字段：`recordDate`、销售额、订单数、利润、
ASP、库存估值和新增会员数。`memberRecharge` 当前不是 `DecisionEngine` 的赋值责任：首次
插入继续依赖表默认值，已有行也不得被当天刷新意外清空。

## 原子持久化语义

| 用例 | SQL 语义 | 并发结果 | 保留的现有行为 |
| --- | --- | --- | --- |
| 补偿 `today-1..today-7` | `INSERT ... ON DUPLICATE KEY UPDATE record_date = record_date`（或等价的、不会写业务列的 insert-if-absent） | 同一日期多请求时仅一方插入，其他请求无唯一键异常且不改已有行。 | 缺失才补、已有历史不重算。 |
| 刷新当天 | `INSERT ... ON DUPLICATE KEY UPDATE sales_amount = VALUES(...), ...` | 至多一行；并发刷新仍为最后一次成功写入者覆盖。 | 每次旧 GET 触发都会以当前输入更新当天。 |

现有唯一键已足够保证每日期一行，因此 **AD-1.3 不应为并发问题新增 Flyway**。也不建议
使用 JVM `synchronized` 或单机内存锁：桌面当前虽通常单实例，但该方案不能跨重启/多实例，
而数据库唯一键已是权威仲裁。

写入应通过独立 writer Bean 的短 `@Transactional` 方法执行；assembler 先完成所有跨所有者
只读查询，再将已计算的值交给 writer。这样不会把 TRADE/UMS/GMS 读取包装进一段长的 HOME
事务，也不会引入跨 Feature 写事务。

## 兼容迁移阶段

### AD-1.3a：命令提取但保持 GET 触发

1. 新增 assembler、command service、writer 与 dashboard query；保留 `DecisionEngineService`
   作为兼容 façade。
2. `/home/count` 仍先调用“补偿 + 当天刷新”命令，再调用纯 dashboard query。
3. 用原子 writer 替换现有 `exists/selectOne + insert/updateById`。
4. 保持路由、响应字段、七日窗口、状态集、`LocalTime.MAX`、告警范围和当天刷新时机。

该阶段已经关闭首建唯一键异常，且把写入位置和查询位置分离；但 HTTP GET 仍具写能力，故不能
宣称 AD-1 完成。

### AD-1.3b：决定并迁移 GET 去写化

仅在用户明确选择刷新新鲜度策略后执行下列之一：

| 策略 | 优点 | 兼容代价/前置条件 |
| --- | --- | --- |
| 定时刷新 | GET 彻底只读、实现范围小。 | 刷新间隔内看板可能不是实时；需明确 cron/频率、启动补偿和失败重试。 |
| 收银/退款事件触发 | 数据更接近实时。 | 涉及 TRADE 写侧事件、事务后触发与失败补偿，超出 AD-1.3a。 |
| 保留兼容刷新入口 | 对现有页面零行为变化。 | GET 仍写；只能作为过渡，不是最终治理。 |

推荐顺序是先完成 AD-1.3a，再在实际页面可接受的最大延迟已获用户确认后选择定时刷新或事件
触发。不得以“架构整洁”为由自行让 `/home/count` 变为可能返回过期当天数据的纯读端点。

## 验收矩阵

| 验收项 | AD-1.3a | AD-1.3b |
| --- | --- | --- |
| 当天首建、顺序重读、历史七日补偿 | 必须通过既有 HOME 特征测试。 | 必须通过，刷新由新触发器完成。 |
| 并发同日首建 | 两个独立事务同时执行，不得抛唯一键错误，最终一行。 | 同左。 |
| 并发当天刷新 | 最终一行且无异常；确认覆盖语义。 | 同左。 |
| HTTP 兼容 | `/home/count` 字段、告警和刷新前行为不变。 | 响应字段不变；只允许已获批的新鲜度差异。 |
| 隔离验证 | HOME 专项、全量 Maven、打包、架构门禁和 `git diff --check`。 | 同左，外加调度/事件触发的故障与重启验证。 |

## 非目标与风险

- 不改变历史补偿所用的“当前库存估值”语义；这不是日终库存快照方案。
- 不合并 HOME 日快照与 FIN/HOME 其他报表口径。
- 不移动 `OmsDailySummary` 的 Entity/Mapper 物理包；该事项属于 AD-2。
- 不新建对外 HTTP 刷新路由；若未来需要运维手工修复入口，需单独定义权限与审计。

AD-1.3a 已具备实施边界；AD-1.3b 的触发策略需要用户授权后才能开始。
