# AD-2.20 第十个共享 Entity 物理归属迁移切片选择

## 结论

第十个物理归属切片选择 **TRADE 的 `OmsOrderLog`**。后续实施任务编号为
**AD-2.21**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.trade.infrastructure.persistence.entity`，并更新
`OmsOrderLogMapper` 与六个 TRADE 生产消费者。实施同时增加 Mapper 插入/读回回归，并扩展既有结账、退款和订单详情
回归来锁定审计日志的写入、查询和只读约束。

`OmsOrderLog` 是订单生命周期审计记录，完全由 TRADE 写入与读取：结账写入结算快照，整单/部分退款追加审计描述，订单详情
在 TRADE 内转换为 `OrderDetailVO.OrderLogVO`。没有其他 Feature、Controller、DTO 契约或 Maven 模块以该 Entity 作为
生产契约。因此移动物理包不会改变 HTTP、跨 Feature 访问或事务边界。

## 已确认的消费面

| 面 | 当前事实 | AD-2.21 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.OmsOrderLog`；显式 `@TableName("oms_order_log")`，继承 `BaseEntity`，包含 `orderId`、`description`、`tenantId` | 原样移至 TRADE 持久化 entity 包；不改字段、基类、审计填充或注解。`BaseEntity` 的 `IdType.ASSIGN_ID` 保持不变。 |
| Mapper | `feature.trade.infrastructure.persistence.mapper.OmsOrderLogMapper extends BaseMapper<OmsOrderLog>`，无自定义 SQL 或 XML | 保留 Mapper 包、名称、扫描根和 MyBatis 扫描方式；只更新泛型导入。 |
| 生产消费者 | `OmsOrderLogMapper`、`OmsOrderLogService`、`OmsOrderLogServiceImpl`、`CheckoutOrchestrator`、`OmsOrderRefundServiceImpl`、`OmsOrderServiceImpl`，均属于 TRADE；后两者目前经 `com.money.entity.*` 使用 | 只更新这六处导入/泛型；不改变结账 JSON 审计快照、整单/部分退款描述、详情排序或写入时机。 |
| 服务与 HTTP | `OmsOrderLogService extends IService<OmsOrderLog>`，但其接口、实现及调用方均在 TRADE。订单详情将日志转换为 `OrderDetailVO.OrderLogVO`，不存在 Entity 返回的 Controller 或跨 Feature 消费者 | 保留本域 `IService` 泛型和 `listByOrderId` 签名；不把本域接口重设计为 DTO，也不改路由、响应字段或异常。 |
| 不可变审计约束 | 服务实现按既有语义拒绝 `updateById`、批量更新和所有删除入口；日志查询依赖 `(order_id, create_time DESC)` 索引 | 不修改禁止审计日志篡改/删除的业务约束、查询排序或索引依赖。 |
| Mapper XML / 资源 FQCN | 未发现 XML、resultMap、参数类型、YAML 或其他资源中的 `OmsOrderLog` / `oms_order_log` Entity FQCN 引用 | 不改资源或 SQL。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `oms_order_log`：非空 `id`、`order_id`、`description`、审计列和 `tenant_id` 默认值，以及 `idx_order_id_time(order_id, create_time DESC)` | 不改表、索引、默认值或 Flyway。 |
| 测试 | 现有 `CheckoutIntegrationTest` 已覆盖结账、整单退款、部分退款和订单服务；当前没有 `OmsOrderLog` 直接测试导入 | 增加本地 Entity/Mapper 测试导入和断言，不改变测试业务场景或事务模型。 |

迁移后所有权登记预计由 **20** 降至 **19**。共享 Entity 的 Feature 文件数是报告型指标：预计仅由 **67** 降至
**64**，因为 `CheckoutOrchestrator` 仍需本域尚未迁移的 `OmsOrder`，而退款/订单查询仍保留其他共享 Entity 的通配符导入。
该计数不是验收目标；跨所有者桥（6）和通配符基线（3）不应因本切片扩大。

## 必须补充的回归

AD-2.21 必须在隔离 `money_pos_test` 中：

1. 新增 `OmsOrderLogMapper` 集成回归，验证 TRADE 本地 Entity 的显式 `oms_order_log` 映射、继承的 `ASSIGN_ID`、
   `orderId`、`description`、`tenantId` 和审计填充字段能够插入并读回；不得以直接 Mapper 的更新/删除测试削弱服务层
   “审计日志不可修改/删除”的既有约束。
2. 扩展 `CheckoutIntegrationTest` 的正常结账、整单退款和部分退款断言，确认三条路径各自追加审计日志；并通过既有
   `OmsOrderService` 订单详情路径确认日志仍按既有 VO 组装与时间排序返回。
3. 不以 Mapper 插入/读回替代生命周期行为回归；不新增任何 GMS、UMS、FIN、HOME 或 SYS 对 TRADE Entity 的生产依赖。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `OmsOrderLog` | TRADE；专用 Mapper、TRADE 服务、结账、退款、订单详情 | 无跨 Feature/HTTP Entity 外泄、无资源 FQCN；已有核心交易回归可扩展，另补 Mapper 插入/读回 | **选择**。 |
| `GmsInventoryOrder` | GMS；Mapper、库存服务和本域 `IService<GmsInventoryOrder>` | 关联库存单主流程，消费面与库存操作语义更宽 | 延后。 |
| `Provinces` / `SysPrintConfig` | SYS；Mapper、`IService<Entity>`、服务/Controller，后者还关联打印运行能力 | 兼容服务泛型、HTTP 或硬件运行面较宽 | 延后。 |
| `UmsRechargeOrder` / `UmsMemberLog` | UMS；资产、充值、日志服务及 Controller | Entity 直接处在 HTTP 或较宽服务面，日志还含本域 `IService` 兼容面 | 延后。 |
| `PosSkuLevelPrice`、`GmsStockLog`、`GmsInventoryDoc`、`SysStrategy` 及其余 Entity | 多个所有者；价格矩阵/Excel/POS 搜索、库存分析、财务查询或系统策略实现 | 消费者、算法、兼容面或跨 Feature 查询实现更宽 | 延后。 |

## AD-2.21 实施与验收边界

1. 只移动 `OmsOrderLog`，更新 Mapper、`OmsOrderLogService`、`OmsOrderLogServiceImpl`、`CheckoutOrchestrator`、
   `OmsOrderRefundServiceImpl`、`OmsOrderServiceImpl` 和新增/调整的 TRADE 测试；不得移动 Mapper、服务、Controller、
   DTO、其他 Entity 或跨域契约。
2. 保留显式 `@TableName("oms_order_log")`、`BaseEntity`、`ASSIGN_ID`、字段 Java 类型、遗留 Mapper 包和
   `com.money.feature.trade.infrastructure.persistence.mapper` 扫描根；不改表/Flyway、HTTP、事务、审计描述或不可变语义。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，并复核共享 Entity
   报告计数与通配符/桥基线没有意外扩大。
4. 运行新增 Mapper、TRADE 结账/退款/订单详情专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和
   `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.21：迁移 `OmsOrderLog` 到 TRADE 持久化实体包，并补 Mapper 与订单审计日志回归**。
