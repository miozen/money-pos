# AD-2.14 第七个共享 Entity 物理归属迁移切片选择

## 结论

第七个物理归属切片选择 **TRADE 的 `OmsOrderPay`**。后续实施任务编号为
**AD-2.15**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.trade.infrastructure.persistence.entity`，并更新遗留
`com.money.mapper.OmsOrderPayMapper`、三个 TRADE 应用消费者及相关测试的导入；同一提交增加 Mapper
CRUD 回归并扩展既有结账支付快照断言。

该 Entity 是订单支付明细的 TRADE 本域持久化记录，不是 HTTP、DTO、Excel 或跨 Feature 契约。FIN 的日报与
交接班读取均经 TRADE 所有者查询契约返回快照，未直接接收该 Entity。AD-2.15 不移动订单主单/明细、Mapper、
服务、Controller 或财务查询契约，也不改变支付、找零、退款和聚合公式。

## 已确认的消费面

| 面 | 当前事实 | AD-2.15 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.OmsOrderPay`；直接 `@TableId(IdType.AUTO)`，无 `@TableName`/`BaseEntity`，含订单号、渠道/标签、旧 `payAmount` 及 `originalAmount`/`netAmount`/`changeAllocated` 支付快照和创建时间 | 原样移至 TRADE 持久化 entity 包；不补字段、基类、审计或新注解。 |
| Mapper | 遗留 `com.money.mapper.OmsOrderPayMapper extends BaseMapper<OmsOrderPay>`，另有 `getDailyPaySummary` 与 `getShiftPayStats` 两条注解聚合 SQL | 保留 Mapper 包、名称、扫描根、所有 SQL 和泛型以外定义；只更新导入。 |
| 应用消费者 | TRADE 的 `CheckoutPaymentService` 创建/重读支付快照；`OmsOrderServiceImpl` 转成订单详情 VO；`RefundAssetHelper` 读取余额支付用于会员资产回退 | 只更新三个 TRADE 导入；不改结账、订单详情、退款、资产回退或事务语义。 |
| FIN 读取 | `FinanceOrderPaymentQueryService`、`FinanceShiftHandoverQueryService` 只调用 Mapper 聚合并返回 `Finance*Snapshot`，不导入 Entity | 不改 FIN 契约、汇总 SQL、净额/找零/退款口径或跨 Feature 边界。 |
| HTTP/DTO/序列化 | 未发现 Controller 或 API DTO 直接暴露 Entity；订单详情将支付记录映射为 `OrderDetailVO.OrderPayVO` | 不改路由、响应字段、DTO 或序列化。 |
| 测试 | `CheckoutIntegrationTest` 覆盖现金、余额、混合支付及回滚中的支付记录；`FinanceFeatureIntegrationTest` 以实体作为本地数据夹具验证聚合 | 迁移时更新测试 FQCN；保留既有业务断言，增加支付快照字段断言和独立 Mapper CRUD。 |
| Mapper XML / 资源 FQCN | 无对应 XML、resultMap、参数类型或旧 FQCN 资源引用；SQL 位于 Mapper 注解 | 不改资源或 SQL。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `oms_order_pay`：自增主键、订单号索引、`pay_amount` 非空默认值、支付快照列、创建时间及现存 `tenant_id` 默认列 | 不改表、索引或 Flyway；不以实体迁移为由补租户字段。 |

实体没有显式 `@TableName`，当前依赖 MyBatis-Plus 的驼峰命名推导到 `oms_order_pay`。这不是 AD-2.15
补注解、改变主键策略或重写聚合 SQL 的授权。迁移后 `CheckoutPaymentService` 将不再是共享 Entity importer，
Feature 共享 Entity 文件数预计由 **72** 降至 **71**；所有权登记由 **23** 降至 **22**，跨域桥和通配符基线不变。

## 必须补充的回归

AD-2.15 必须在隔离 `money_pos_test` 中：

1. 新增 `OmsOrderPayMapper` CRUD 集成回归，验证 TRADE 本地 Entity 的 `AUTO` 主键、隐式表名、订单号、渠道、旧
   净收字段、实收/净额/找零快照和显式创建时间可插入读回、更新与删除。
2. 扩展既有现金结账断言，锁定一条支付记录的订单号、渠道代码/名称、`payAmount`、`originalAmount`、
   `netAmount` 和 `changeAllocated`；继续保留余额、混合支付及失败回滚回归。
3. 保留 `FinanceFeatureIntegrationTest` 的支付聚合夹具与断言，迁移其中的测试导入，不让 FIN 生产代码重新接收
   Entity；直接 Mapper CRUD 不验证或重写日报/交接班 SQL 公式。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `OmsOrderPay` | TRADE；三个 TRADE 应用消费者和遗留 Mapper，FIN 仅使用 TRADE 快照查询 | 无 HTTP/跨域 Entity 面；结账、退款和财务聚合已有回归，可补直接 CRUD 锁定隐式映射和支付快照 | **选择**。 |
| `UmsRechargeOrder` | UMS；充值服务、资产查询、Mapper 及充值详情 Controller | Controller 直接返回 Entity，迁移将扩大为 HTTP 返回类型兼容改造 | 延后。 |
| `GmsInventoryOrder` | GMS；Mapper、库存服务及 `IService<GmsInventoryOrder>` | 迁移会改变公开服务泛型 | 延后。 |
| `Provinces` / `SysPrintConfig` | SYS；Mapper、`IService<Entity>`、服务/Controller，后者还关联打印能力 | 兼容服务泛型及运行能力/HTTP 面较宽 | 延后。 |
| `OmsOrderLog` | TRADE；结账、订单查询、退款及 `IService<OmsOrderLog>` | 虽属本域，但服务泛型与操作日志写入面更宽 | 延后。 |
| `GmsGoodsCombo`、`GmsStockLog`、`GmsInventoryDoc` 及其余 Entity | 多个所有者；商品库存、分析、交易或跨 Feature 查询实现 | 消费者、算法或兼容面更宽 | 延后。 |

## AD-2.15 实施与验收边界

1. 只移动 `OmsOrderPay`，更新 `OmsOrderPayMapper`、`CheckoutPaymentService`、`OmsOrderServiceImpl`、
   `RefundAssetHelper` 和相关测试导入；不得移动 Mapper、订单/支付服务、Controller、其他 Entity 或契约。
2. 保留 `IdType.AUTO`、隐式表名、字段 Java 类型、遗留 Mapper 包及 `com.money.mapper` 扫描根；不改表/Flyway、
   HTTP、DTO、事务、支付/找零/退款公式或 Mapper 聚合 SQL。
3. `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 共享 Entity 文件数预期减少一项。
4. 运行新增 Mapper 与结账专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.15：迁移 `OmsOrderPay` 到 TRADE 持久化实体包，并增加支付快照与 Mapper CRUD 回归**。
