# AD-2.18 第九个共享 Entity 物理归属迁移切片选择

## 结论

第九个物理归属切片选择 **GMS 的 `GmsGoodsCombo`**。后续实施任务编号为
**AD-2.19**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，并更新遗留
`com.money.mapper.GmsGoodsComboMapper`、四个 GMS 生产消费者及两处相关测试导入；同一提交增加 Mapper
CRUD 回归并保留套餐配置、库存联动及套餐结账/退款回归。

该 Entity 是商品套餐 BOM 行的 GMS 本域持久化记录。`GmsGoodsComboService` 负责覆盖式配置和按套餐分组读取，
`GmsGoodsServiceImpl` 组装商品详情 DTO，`GmsGoodsStockService` 处理后台库存联动，
`GmsStockCommandService` 在 GMS 所有的收银/退款库存命令实现中展开套餐。TRADE 不直接导入生产 Entity；它只向
GMS 库存命令发送快照行，套餐实物扣减仍由 GMS 执行。因此此迁移不会改变跨 Feature 契约或结账事务边界。

## 已确认的消费面

| 面 | 当前事实 | AD-2.19 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsGoodsCombo`；`@TableId(IdType.ASSIGN_ID)`，无 `@TableName`/基类，含套餐商品、子商品、数量与创建时间字段 | 原样移至 GMS 持久化 entity 包；不补字段、基类、审计或注解。 |
| Mapper | 遗留 `com.money.mapper.GmsGoodsComboMapper extends BaseMapper<GmsGoodsCombo>`，无自定义 SQL 或 XML | 保留 Mapper 包、名称、扫描根、泛型之外的定义和 MyBatis 扫描方式；只更新导入。 |
| 应用消费者 | GMS 的 `GmsGoodsComboService`、`GmsGoodsServiceImpl`、`GmsGoodsStockService`、`GmsStockCommandService` | 只更新四个 GMS 导入；不改 BOM 覆盖策略、详情 DTO 组装、后台库存联动、收银扣减、退款恢复或事务语义。 |
| TRADE/HTTP/DTO | 无 TRADE 生产 Entity 导入；`GmsGoodsComboDTO` 是配置输入 DTO，Controller 不返回 Entity。TRADE 测试仅以 Entity 建立套餐夹具，实际结账经 GMS `SaleStockCommand` / `RefundStockCommand` | 不改路由、响应字段、`GmsGoodsComboDTO`、库存命令契约、结账/退款流程或测试业务断言。 |
| 测试 | `GmsFeatureIntegrationTest` 覆盖套餐配置、套餐/子商品库存联动；`CheckoutIntegrationTest` 覆盖套餐结账及全额退款使套餐配额和子商品库存恢复 | 迁移两处测试 FQCN；保留既有行为断言，另加独立 Mapper CRUD。 |
| Mapper XML / 资源 FQCN | 无 XML、resultMap、参数类型或旧 FQCN 资源引用 | 不改资源或 SQL。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `gms_goods_combo`：`id` 自增、套餐/子商品非空、数量默认 1、创建时间、`tenant_id` 默认 0 和 `idx_combo_id` | 不改表、索引、默认值或 Flyway；不以 Entity 迁移为由协调 Java `ASSIGN_ID` 与历史表自增定义。 |

实体没有显式 `@TableName`，当前依赖 MyBatis-Plus 驼峰命名推导到 `gms_goods_combo`。表的 `id` 为历史自增，
但 Java 注解为 `ASSIGN_ID`；两者并存是已运行语义。AD-2.19 只能通过回归锁定现状，不能借迁移调整主键策略、
表映射或 Mapper 扫描。迁移后四个 Feature 共享 Entity importer 将消失；所有权登记预计由 **21** 降至 **20**，
Feature 共享 Entity import 文件数预计由 **68** 降至 **64**，跨域桥和通配符基线不变。

## 必须补充的回归

AD-2.19 必须在隔离 `money_pos_test` 中：

1. 新增 `GmsGoodsComboMapper` CRUD 集成回归，验证 GMS 本地 Entity 的 `ASSIGN_ID`、隐式表名、套餐商品 ID、
   子商品 ID、数量和创建时间可插入读回、更新与删除；不重写任何 SQL。
2. 保留 `GmsFeatureIntegrationTest` 的套餐配置、套餐详情与后台库存穿透断言，并更新其测试导入。
3. 保留 `CheckoutIntegrationTest` 的套餐结账和全额退款库存恢复断言，并更新其中的夹具导入；该测试证明
   TRADE 继续只调用 GMS 库存命令，不重新接收套餐 Entity。
4. 不以 Mapper CRUD 替代套餐库存行为回归，也不新增对 GMS Entity 的跨 Feature 生产依赖。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `GmsGoodsCombo` | GMS；遗留 Mapper、四个 GMS 商品/库存消费者 | 无 HTTP、公开泛型或跨 Feature Entity 面；已有套餐配置、库存联动、结账/退款回归，可补直接 CRUD 锁定隐式表名和主键现状 | **选择**。 |
| `GmsInventoryOrder` | GMS；Mapper、库存服务及 `IService<GmsInventoryOrder>` | 迁移会改变公开服务泛型 | 延后。 |
| `Provinces` / `SysPrintConfig` | SYS；Mapper、`IService<Entity>`、服务/Controller，后者还关联打印运行能力 | 兼容服务泛型、HTTP 或硬件运行面较宽 | 延后。 |
| `UmsRechargeOrder` / `UmsMemberLog` | UMS；资产/充值/日志服务与 Controller，后者另有 `IService<UmsMemberLog>` | Entity 直接出现在 HTTP 返回或公开服务泛型中 | 延后。 |
| `OmsOrderLog` | TRADE；结账、退款、订单查询及 `IService<OmsOrderLog>` | 本域但服务泛型与日志写入面更宽 | 延后。 |
| `PosSkuLevelPrice`、`GmsStockLog`、`GmsInventoryDoc`、`SysStrategy` 及其余 Entity | 多个所有者；价格矩阵/Excel/POS 搜索、库存分析、财务查询或系统策略实现 | 消费者、算法、兼容面或跨 Feature 查询实现更宽 | 延后。 |

## AD-2.19 实施与验收边界

1. 只移动 `GmsGoodsCombo`，更新 `GmsGoodsComboMapper`、`GmsGoodsComboService`、`GmsGoodsServiceImpl`、
   `GmsGoodsStockService`、`GmsStockCommandService` 和两处相关测试导入；不得移动 Mapper、服务、Controller、
   DTO、其他 Entity 或库存命令契约。
2. 保留 `IdType.ASSIGN_ID`、隐式表名、字段 Java 类型、遗留 Mapper 包及 `com.money.mapper` 扫描根；不改表/Flyway、
   HTTP、DTO、事务、套餐覆盖策略、库存公式或结账/退款逻辑。
3. `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 共享 Entity 文件数
   预期减少四项。
4. 运行新增 Mapper、GMS 套餐及 TRADE 套餐结账专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和
   `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.19：迁移 `GmsGoodsCombo` 到 GMS 持久化实体包，并补 Mapper 与套餐库存回归**。
