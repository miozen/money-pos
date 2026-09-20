# AD-2.16 第八个共享 Entity 物理归属迁移切片选择

## 结论

第八个物理归属切片选择 **HOME 的 `OmsDailySummary`**。后续实施任务编号为
**AD-2.17**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.home.infrastructure.persistence.entity`，并更新遗留
`com.money.mapper.OmsDailySummaryMapper`、三个 HOME 应用消费者及 HOME 快照测试的导入；同一提交补足
Mapper 持久化回归并保留现有日快照端到端特征测试。

尽管名称沿用 `Oms`，该 Entity 是 HOME 独有的每日经营读模型：HOME 负责生成、补偿、原子写入、读取七日均值和
仪表盘组装；订单、库存估值与新增会员数已通过 TRADE/GMS/UMS 的 Entity-free 查询契约输入。没有 Controller、DTO、
XML FQCN、其他 Maven 模块或跨 Feature 契约直接暴露该 Entity。因此它是当前剩余候选中唯一生产消费面完全收敛至
所有者、又已有完整行为保护的最小切片。

## 已确认的消费面

| 面 | 当前事实 | AD-2.17 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.OmsDailySummary`，继承 `BaseEntity`，显式 `@TableName("oms_daily_summary")` 与 `@TableId(IdType.AUTO)`；含日期、销售/订单/利润/ASP/库存、会员充值和新增会员字段 | 原样移至 HOME 持久化 entity 包；不补字段、基类、审计或注解。 |
| Mapper | 遗留 `com.money.mapper.OmsDailySummaryMapper extends BaseMapper<OmsDailySummary>`；两个注解 SQL 分别为 `insertIfAbsent` 和 `upsertSnapshot` | 保留 Mapper 包、名称、扫描根及全部 SQL；只更新泛型导入。 |
| 应用消费者 | HOME 的 `HomeDailySummaryQueryService` 以 Lambda 查询日期/空快照并读取七日均值；`HomeDailySummaryWriter` 组装并原子写入；`HomeDashboardQueryService` 将快照组装为仪表盘 | 只更新三个 HOME 导入；不改查询、补偿、刷新、告警、仪表盘或事务语义。 |
| HTTP/DTO/跨域 | `HomeController` 仅调用 HOME 服务并返回 Map/既有响应；TRADE/GMS/UMS 只提供输入快照契约，不接收或返回该 Entity | 不改路由、响应字段、DTO、序列化或任何所有者查询契约。 |
| 测试 | `HomeCountSnapshotCharacterizationTest` 已覆盖启动/定时刷新后 GET 纯读、今日 upsert 保持同一 ID、保留 `memberRecharge`、七日补偿边界、日快照与大盘口径，以及 UMS 新会员输入 | 迁移测试 FQCN，保留所有现有断言；另补直接 Mapper 回归锁定映射与两个原子写入分支。 |
| Mapper XML / 资源 FQCN | 无 XML、resultMap、参数类型或旧 FQCN 资源引用；SQL 均在 Mapper 注解中 | 不改资源或 SQL。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `oms_daily_summary`：自增主键、显式快照列、`member_recharge`、`tenant_id` 默认列以及唯一键 `uk_record_date` | 不改表、索引、约束或 Flyway；不以实体迁移为由改变单日唯一记录语义。 |

`insertIfAbsent` 的冲突分支只能保持既有行，而 `upsertSnapshot` 只能更新快照负责的六项指标和更新时间；二者都没有写入
`member_recharge`。这个“保留充值值”的历史语义是 AD-2.17 的硬边界，不能因物理包迁移改写 SQL 或误当作字段缺失修复。
迁移后 HOME 的三个共享 Entity importer 将消失；所有权登记预计由 **22** 降至 **21**，Feature 共享 Entity import
文件数预计由 **71** 降至 **68**，其余跨域桥和通配符基线不变。

## 必须补充的回归

AD-2.17 必须在隔离 `money_pos_test` 中：

1. 增加或明确扩展 `OmsDailySummaryMapper` 持久化集成回归：验证 HOME 本地 Entity 的显式表名、`AUTO` 主键、日期和
   全部快照字段可 CRUD；验证同一 `record_date` 的 `insertIfAbsent` 不覆盖既有快照，`upsertSnapshot` 保持同一记录并
   更新其负责字段，且两条 SQL 都保留既有 `member_recharge`。
2. 保留并更新 `HomeCountSnapshotCharacterizationTest`：定时刷新创建/更新今日快照、`GET /home/count` 不写、七日缺失
   补偿范围、唯一日期记录、仪表盘/日快照各自订单口径和 UMS 新会员计数均不得回退。
3. 不以 Mapper CRUD 替代端到端快照特征化；也不新增对 TRADE/GMS/UMS Entity、Mapper 或实现服务的依赖。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `OmsDailySummary` | HOME；三个 HOME 应用消费者和遗留 Mapper | 无 HTTP/跨域 Entity 面；已有日快照、补偿、原子更新及 `memberRecharge` 保留回归，可补直接 Mapper 语义保护 | **选择**。 |
| `UmsRechargeOrder` | UMS；充值、资产查询、Mapper 与充值详情 Controller | Controller 直接返回 Entity，迁移会扩大为 HTTP 返回类型兼容改造 | 延后。 |
| `GmsInventoryOrder` | GMS；Mapper、库存服务及 `IService<GmsInventoryOrder>` | 迁移会改变公开服务泛型 | 延后。 |
| `Provinces` / `SysPrintConfig` | SYS；Mapper、`IService<Entity>`、服务/Controller，后者还关联打印运行能力 | 兼容服务泛型、HTTP 或硬件运行面较宽 | 延后。 |
| `OmsOrderLog` | TRADE；结账、订单查询、退款及 `IService<OmsOrderLog>` | 本域但服务泛型与操作日志写入面更宽 | 延后。 |
| `GmsGoodsCombo`、`GmsStockLog`、`GmsInventoryDoc`、`SysStrategy` 及其余 Entity | 多个所有者；商品库存、分析、交易或系统策略实现 | 消费者、算法、兼容面或跨 Feature 查询实现更宽 | 延后。 |

## AD-2.17 实施与验收边界

1. 只移动 `OmsDailySummary`，更新 `OmsDailySummaryMapper`、`HomeDailySummaryQueryService`、
   `HomeDailySummaryWriter`、`HomeDashboardQueryService` 和相关 HOME 测试导入；不得移动 Mapper、HOME 服务、
   Controller、其他 Entity 或查询契约。
2. 保留 `BaseEntity`、`@TableName("oms_daily_summary")`、`IdType.AUTO`、字段 Java 类型、遗留 Mapper 包及
   `com.money.mapper` 扫描根；不改表/Flyway、HTTP、DTO、调度、事务、补偿窗口、告警/仪表盘公式或 Mapper SQL。
3. `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 共享 Entity 文件数
   预期减少三项。
4. 运行新增 Mapper 与 HOME 快照专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.17：迁移 `OmsDailySummary` 到 HOME 持久化实体包，并补 Mapper 与日快照回归**。
