# AD-2.4 第二个共享 Entity 物理归属切片选择

## 结论

第二个物理归属切片选择 **GMS 的 `GmsTurnoverWarningSnapshot`**。后续实施任务编号为
**AD-2.5**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，更新 GMS 服务和现有 GMS Mapper
导入，并增加周转快照行为回归。

该选择不把 Entity 迁移本身与 SYS 策略契约、周转算法、Controller 路由、Excel 导出、表结构或快照写入
时机混在一起。AD-2.5 也不得扩展 AD-2.3.1 的跨域兼容基线。

## 已确认的消费面

| 面 | 当前事实 | AD-2.5 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsTurnoverWarningSnapshot`，`@TableName("gms_turnover_warning_snapshot")`、JSON `JacksonTypeHandler`、日期与创建/更新时间字段 | 原样移动注解和字段到 GMS 持久化 entity 包。 |
| Mapper | `feature.gms.infrastructure.persistence.mapper.GmsTurnoverWarningSnapshotMapper` 是 `BaseMapper<GmsTurnoverWarningSnapshot>`，已标注 `@Mapper` 和 `@InterceptorIgnore(tenantLine = "true")` | Mapper 包、注解和扫描根不变，只更新泛型导入。 |
| 应用消费者 | 仅 `feature.gms.application.turnover.GmsTurnoverServiceImpl`。它在当日快照 upsert、近 30 天趋势读取和 JSON 反序列化中使用该 Entity | 只更新导入，不改变算法、排序、Top20 截断或异常吞没语义。 |
| HTTP/DTO/序列化 | `GmsTurnoverController` 只公开 `TurnoverDashboardVO` 与 `Map<String,Object>`；未发现 Controller、DTO、Excel 或 API 契约暴露该 Entity | 路由 `/gms/analysis/turnover-warnings`、`/turnover-warning-trend` 和导出接口不变。 |
| Mapper XML / 资源 FQCN | Java、测试及资源目录中没有 XML `resultMap`、参数类型或 FQCN 引用 | 不改 XML/资源。 |
| Spring 装配 | `MybatisConfig` 已扫描 `com.money.feature.gms.infrastructure.persistence.mapper` | 不改扫描配置。 |
| 表/Flyway | `V1.0.2__create_turnover_snapshot.sql` 的 `gms_turnover_warning_snapshot`，含 `uk_snapshot_date` 唯一键 | 不改表、索引或 Flyway。 |

当前生产引用仅为 GMS 服务与 GMS Mapper 两处；API Entity 定义是第三处源码命中。不存在跨 Feature
Entity 消费，因此删除共享 Entity import 后，AD-2.3.1 的精确跨域桥基线不需要新增或放宽。

## 必须补充的行为回归

当前没有 `GmsTurnoverWarningSnapshot` 的专项测试。AD-2.5 在移动前或同一提交中必须增加隔离
`money_pos_test` 集成回归，至少锁定：

1. 调用 `GmsTurnoverService.getTurnoverWarnings()` 会为当天创建快照；重复调用仍只保留当天一条记录，且以
   最新补货/滞销数量及 Top20 JSON 更新。
2. `getWarningTrend()` 返回按日期升序的近 30 天快照计数，并从已持久化 JSON 计算补货与滞销黑榜频次。
3. 快照持久化异常继续被当前实现吞没，不能阻断 `getTurnoverWarnings()` 返回预警结果。

这些测试应通过服务与 Mapper 观察已有业务结果；不将 Entity 作为新的跨 Feature 或 HTTP 契约。若现有
数据库夹具无法稳定构造周转源数据，先在 GMS 测试范围补最小 Mapper/服务夹具，不改周转判定公式。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `GmsTurnoverWarningSnapshot` | GMS；GMS 服务 + 已归属 GMS Mapper | 需补快照/趋势回归，但持久化、Controller 输出和唯一键语义清晰 | **选择**。 |
| `GmsMemberTransaction` | UMS；仅遗留 `com.money.mapper.GmsMemberTransactionMapper` | Entity 未标 `@TableName`，没有 Feature 应用消费者、路由或行为测试；无法证明迁移后 Mapper 推导/装配语义 | 延后。 |
| `PosMemberLevel` | UMS；仅遗留 `com.money.mapper.PosMemberLevelMapper` | 同样缺少应用消费者与回归，且 Mapper 尚未归属 UMS 持久化包 | 延后。 |
| `OmsDailySummary` | HOME；三项 HOME 应用服务 + 遗留 Mapper + HOME 特征测试 | 回归很强，但涉及查询空对象、原子写入、七日补偿与 Dashboard 内部传递，消费面显著大于本轮候选 | 延后为独立 HOME 切片。 |
| SYS 配置/打印/参考 Entity | SYS / 运行时或系统模块边界 | 分别涉及策略契约、硬件打印或 `money-app-system` 物理边界 | 不作为本轮 GMS 最小迁移。 |

## AD-2.5 实施与验收边界

1. 只移动 `GmsTurnoverWarningSnapshot` Java 实体，更新 `GmsTurnoverServiceImpl` 与
   `GmsTurnoverWarningSnapshotMapper` 的导入；不移动 `GmsTurnoverMapper`、`SysStrategyMapper`、Controller
   或任何其他 Entity。
2. 保留 `@TableName(..., autoResultMap = true)`、`JacksonTypeHandler`、`@InterceptorIgnore`、表名和
   `uk_snapshot_date` 所表达的每日单记录语义。
3. `rg` 确认 API 模块不再有旧 Entity、业务 Java/测试/资源无旧 FQCN，且共享 Entity 扫描按实际减少。
4. 运行新增周转专项回归、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.5：迁移 `GmsTurnoverWarningSnapshot` 到 GMS 持久化实体包，并增加周转快照回归**。
