# AD-2.22 第十一个共享 Entity 物理归属迁移切片选择

## 结论

第十一个物理归属切片选择 **GMS 的 `GmsInventoryOrder`**。后续实施任务编号为
**AD-2.23**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，并更新已在 GMS 扫描根中的
`GmsInventoryOrderMapper`、本域 `GmsInventoryOrderService` 与其实现。实施同时增加 Mapper CRUD 回归及库存单主流程
回归，保留采购入库、库存盘点和报损出库的原有事务与库存/成本/流水语义。

`GmsInventoryOrder` 是 GMS 库存单主记录。生产源码实际仅在 GMS Mapper、服务接口和实现中出现；三个命令只接收
`GmsInventoryOrderDTO` / `GmsInventoryOrderDetailDTO`，不把 Entity 作为 HTTP、DTO、JSON、Excel 或跨 Feature 契约。
尽管服务保留 `IService<GmsInventoryOrder>` 泛型，实查没有 Controller、其他 Feature、其他 Maven 模块或资源层消费者；该泛型
完全限于 GMS 本域。因此本次物理迁移不需要扩展成契约或 API 改造。

## 已确认的消费面

| 面 | 当前事实 | AD-2.23 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsInventoryOrder`；直接声明 `@TableId(IdType.ASSIGN_ID)`，无 `@TableName` / 基类，包含单号、类型、总金额、状态、备注、创建/更新时间和租户字段 | 原样移至 GMS 持久化 entity 包；不补基类、审计字段或注解，不改字段类型。 |
| Mapper | `feature.gms.infrastructure.persistence.mapper.GmsInventoryOrderMapper extends BaseMapper<GmsInventoryOrder>`，无自定义 SQL 或 XML | 保留 Mapper 包、名称、扫描根及 MyBatis 扫描方式；只更新泛型导入。 |
| 应用消费者 | `GmsInventoryOrderService extends IService<GmsInventoryOrder>`，`GmsInventoryOrderServiceImpl` 在采购入库、盘点、报损三个 `@Transactional` 命令中创建主单 | 只更新接口和实现导入/泛型；不改本域 `IService`、单号生成、命令签名、主单写入时机或事务。 |
| HTTP/DTO/跨域 | 生产全量搜索未发现 Controller、其他 Feature、DTO/VO、序列化、Excel、其他 Maven 模块或跨 Feature 契约直接引用该 Entity；命令输入是库存 DTO | 不改路由、响应字段、DTO、序列化或新增兼容桥。 |
| 库存聚合行为 | 入库按移动加权平均更新库存和成本并写 `INBOUND` 流水；盘点按差量更新并写 `CHECK` 流水；报损扣减库存并写 `SCRAP` 流水。主单均为 `COMPLETED`，明细已使用 GMS 本地 `GmsInventoryOrderDetail` | 不改库存量、均价、旧 `purchasePrice` 双写、状态、流水、明细插入顺序或异常/事务语义。 |
| Mapper XML / 资源 FQCN | 未发现 XML、resultMap、参数类型、YAML 或其他资源中的 `GmsInventoryOrder` / `gms_inventory_order` Entity FQCN 引用 | 不改资源或 SQL。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `gms_inventory_order`：非空分配式主键、唯一 `order_no`、类型/状态、金额/备注、时间及 `tenant_id` 默认值 | 不改表、唯一索引、默认值或 Flyway；不以迁移改变主键或隐式命名语义。 |

实体没有显式 `@TableName`，当前依赖 MyBatis-Plus 驼峰命名推导到 `gms_inventory_order`；该隐式映射和
`ASSIGN_ID` 是 AD-2.23 的固定运行语义。迁移后所有权登记预计由 **19** 降至 **18**，Feature 共享 Entity import
文件预计由 **64** 降至 **62**：Mapper 与服务接口不再导入共享包，而服务实现仍会导入 `GmsGoods`、`GmsStockLog`。
这些计数只是报告指标；跨所有者桥（6）和通配符基线（3）不得扩大。

## 必须补充的回归

AD-2.23 必须在隔离 `money_pos_test` 中：

1. 新增 `GmsInventoryOrderMapper` CRUD 集成回归，验证 GMS 本地 Entity 的 `ASSIGN_ID`、隐式表名、单号、类型、金额、
   状态、备注、创建/更新时间和租户字段可插入读回、更新与删除；不增加自定义 SQL。
2. 新增或扩展 GMS 库存单服务集成回归，分别调用采购入库、盘点和报损命令，验证每条路径写入正确类型及 `COMPLETED`
   主单、明细与库存流水，并保持各自的库存/成本结果与事务边界。
3. 不以 Mapper CRUD 替代库存命令行为回归；不新增 TRADE、UMS、FIN、HOME 或 SYS 对 GMS Entity 的生产依赖。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `GmsInventoryOrder` | GMS；GMS 专用 Mapper、本域服务接口/实现、三个库存命令 | 无跨 Feature/HTTP/资源 Entity 面；服务泛型经实查仅在本域，可补 Mapper CRUD 与入库/盘点/报损端到端回归 | **选择**。 |
| `Provinces` / `SysPrintConfig` | SYS；Mapper、`IService<Entity>`、服务/Controller，后者还关联打印运行能力 | 兼容服务泛型、HTTP 或硬件运行面较宽 | 延后。 |
| `UmsRechargeOrder` / `UmsMemberLog` | UMS；资产、充值、日志服务及 Controller | Entity 直接处在 HTTP 或较宽服务面，日志还含本域 `IService` 兼容面 | 延后。 |
| `GmsInventoryDoc` / `GmsStockLog` | GMS；库存服务、分析、查询/Mapper，前者还有 FIN 查询实现 | 消费者、算法或跨 Feature 查询实现更宽 | 延后。 |
| `GmsGoods`、`GmsBrand`、`GmsGoodsCategory`、`PosSkuLevelPrice`、`OmsOrder`、`OmsOrderDetail`、会员/券与 SYS 其余 Entity | 多个所有者；商品目录、价格矩阵、交易、会员资产、系统配置或兼容桥 | 消费者、算法、公开兼容面或跨 Feature 查询更宽 | 延后。 |

## AD-2.23 实施与验收边界

1. 只移动 `GmsInventoryOrder`，更新 `GmsInventoryOrderMapper`、`GmsInventoryOrderService`、
   `GmsInventoryOrderServiceImpl` 和新增/调整的 GMS 测试；不得移动 Mapper、Controller、DTO、其他 Entity 或跨域契约。
2. 保留 `IdType.ASSIGN_ID`、隐式表名、字段 Java 类型、GMS Mapper 包及
   `com.money.feature.gms.infrastructure.persistence.mapper` 扫描根；不改表/Flyway、HTTP、DTO、事务、库存/成本/流水算法。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，并复核共享 Entity
   报告计数与通配符/桥基线没有意外扩大。
4. 运行新增 Mapper、GMS 入库/盘点/报损专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和
   `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.23：迁移 `GmsInventoryOrder` 到 GMS 持久化实体包，并补 Mapper 与库存单主流程回归**。
