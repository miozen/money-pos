# AD-2.30 第十五个共享 Entity 物理归属迁移切片选择

## 结论

第十五个物理归属切片选择 **GMS 的 `GmsInventoryDoc`**。后续实施任务编号为
**AD-2.31**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，更新遗留
`GmsInventoryDocMapper`、四个 GMS 库存/财务快照消费者及受影响测试导入，并补库存单据 Mapper 与既有库存、结账、财务快照回归。

这是当前 15 个已登记共享 Entity 中边界最小的真实所有者切片。生产直接使用仅有一个遗留 Mapper 和四个 GMS
消费者：`GmsInventoryDocServiceImpl`、`GmsStockCommandService`、`FinanceInventoryDocumentQueryService` 与
`FinanceWaterfallInventoryQueryService`。名称带有 Finance 的后两者实际位于 GMS，向 FIN 输出既有的
Entity-free `FinanceInventoryDocumentSnapshot` / `FinanceWaterfallProcurementSnapshot` 契约；FIN 不直接取得实体。
库存 Controller 仅接收 `InventoryDocRequestDTO` 并调用 GMS 服务，也不暴露实体。没有其他 Feature、Maven 模块、DTO/VO、
JSON/Excel 适配器、Mapper XML、resultMap、YAML 或资源 FQCN 使用该类型。因此迁移只改本地持久化导入，不能改库存、
结账或财务契约。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.31 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsInventoryDoc`；显式 `@TableName("gms_inventory_doc")`，继承 `BaseEntity`，字段为单号、类型、总数量、总金额、经办人、备注和租户 | 原样移至 GMS 持久化 entity 包；不改表名、基类、字段、Java 类型、审计填充或默认值。 |
| 主键与表 | `BaseEntity` 的既有 `IdType.ASSIGN_ID` 与表的 `AUTO_INCREMENT id` 历史共存；`gms_inventory_doc` 有 `uk_doc_no`，并包含审计列、租户列及金额/数量默认值 | 保持既有主键运行行为、唯一键、列、索引、默认值和 Flyway；不得借迁移改 AUTO/ASSIGN_ID、单号生成或表定义。 |
| Mapper | 遗留 `com.money.mapper.GmsInventoryDocMapper extends BaseMapper<GmsInventoryDoc>`，无自定义方法、注解 SQL 或 XML | Mapper 包、名称和扫描根不动；只更新泛型导入。 |
| GMS 库存写入 | `GmsInventoryDocServiceImpl.executeDoc` 在同一事务内处理 INBOUND/CHECK/OUTBOUND 的数量、成本、明细、流水和主单；`GmsStockCommandService` 在结账/退款库存命令中写 `SALE_OUT`/`RETURN` 主单及其明细、流水 | 只更新实体与 Lambda 引用；不改三种库存命令、加权成本、库存增减、单号前缀/格式、结账/退款写入顺序、异常、事务或明细/流水联动。 |
| FIN 读侧 | 两个 GMS 内部适配器按库存单读取聚合，再映射为 API 快照；FIN 经 `FinanceInventoryDocumentQuery`、`FinanceWaterfallInventoryQuery` 读取而不导入 Entity/Mapper | 保留 SQL 条件、日期范围、`INBOUND`/`OUTBOUND`/`CHECK` 口径、金额转换、排序和 Entity-free 快照契约；不得为迁包新增跨域实体 import。 |
| HTTP / 测试 | `GmsInventoryController` 的三个路由只传 DTO；现有 `GmsFeatureIntegrationTest` 验证入库主单/明细，`CheckoutIntegrationTest` 验证结账 `SALE_OUT` 主单，`FinanceFeatureIntegrationTest` 以测试夹具写入财务/瀑布流单据 | Controller 路由、鉴权、DTO 和响应不改；更新上述测试的本地类型导入，并增加专门 Mapper 持久化回归。 |
| 跨域 / 资源 | 生产无其他 Feature Entity 使用；FIN 与 TRADE 测试因夹具/断言直接引用 Mapper/Entity，但不是生产契约。全量搜索未发现 XML、resultMap、YAML、SQL 资源或 API 消费模块 FQCN | 不新增 API 兼容副本、跨域桥或 DTO；迁移时同步修正测试 FQCN，确认资源目录无旧 FQCN。 |
| Flyway | `V1.0.0__init_database.sql` 创建 `gms_inventory_doc`，无种子主单 | 不改表、Flyway、唯一键或数据库数据；不新增迁移。 |

`GmsInventoryDoc` 是 GMS 的库存主单持久化模型，不是 FIN 或 TRADE 的跨域输入/输出。FIN 已通过快照契约取得
读模型，TRADE 已通过库存命令触发写入；因此物理迁移不应新建兼容副本，也不应反向让 FIN/TRADE 使用 GMS
基础设施实体。库存主单的 `BaseEntity` 分配式主键与 DDL 自增列虽然不对称，却是已验证的历史运行语义；本任务只能
特征化、保留，不能顺带修复。

迁移后所有权登记预计由 **15** 降至 **14**。四个 Feature 内本域显式 import 将移除；其中
`GmsStockCommandService` 和 `GmsInventoryDocServiceImpl` 仍导入其他共享 Entity，两个 FIN 快照适配器不再有共享
Entity import，故共享 Entity import 文件报告预计由 **62** 降至 **60**，owner-local uses 预计由 **95** 降至
**91**。跨所有者桥（6）与通配符基线（3）不得扩大；这些均是审计指标，最终以实际扫描为准。

## AD-2.31 必须补充的回归

1. 新增 `GmsInventoryDocMapperIntegrationTest`，在隔离 `money_pos_test` 中以 GMS 本地 Entity 写入并读回唯一单号
   的主单，断言显式表映射、既有 ID 行为、类型、数量、金额、经办人、备注、租户和继承审计字段；再更新同一主单的
   合法汇总字段并读回。测试不得修改主键策略、表/索引或新增替代 SQL。
2. 保留并更新 `GmsFeatureIntegrationTest` 的入库断言：库存、入库主单、明细快照与成本字段仍一致；覆盖
   `GmsInventoryDocServiceImpl` 的主单落库路径，而非只测试 Mapper。
3. 保留并更新 `CheckoutIntegrationTest`：结账仍创建 `XS-<requestId>` 的 `SALE_OUT` 主单；同时保留既有退款路径的
   `RETURN` 主单及库存/明细/流水回归，确保 TRADE 仍只经库存命令协作。
4. 保留并更新 `FinanceFeatureIntegrationTest` 的两类夹具：`OUTBOUND`/`CHECK` 财务日单与 `INBOUND` 瀑布流日聚合
   的快照口径、日期范围、金额和排序不变，证明 FIN 没有获得实体契约。
5. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 与 `git diff --check`；确认 API
   模块无旧 Entity，业务 Java/测试/资源无旧 FQCN，登记减少一项且桥/通配符没有扩大。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `GmsInventoryDoc` | GMS；单 Mapper、四个 GMS 库存/快照消费者，FIN/TRADE 已使用 Entity-free 契约 | 写入路径较关键，但所有生产引用同域，已有库存、结账、退款与财务快照回归可锁定 | **选择**。 |
| `SysBrandConfig` | SYS；Mapper/配置服务，且 GMS Excel 与遗留 POS 商品门面仍直接读取 | 存在 GMS→SYS 配置兼容面；物理迁移会触及已登记跨所有者使用 | 延后。 |
| `SysStrategy` | SYS；Mapper/管理服务，GMS 周转和 FIN 流量策略仍直接读取 | 两个跨所有者策略读取面，须先收敛为窄契约 | 延后。 |
| `GmsStockLog`、`GmsGoods`、`GmsBrand`、`GmsGoodsCategory`、`PosSkuLevelPrice` | GMS；库存流水、商品目录、价格矩阵、Excel/POS 查询 | 消费者、算法或公开兼容面更宽 | 延后。 |
| `UmsMember`、`UmsMemberBrandLevel`、`UmsMemberLog`、`PosMemberCoupon`、`PosCouponRule` | UMS；会员/资产/券工作流及既有桥 | 事务与公开/跨所有者兼容面更宽 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` | TRADE；结账、退款、订单聚合和查询 | 交易主流程与订单兼容面显著更宽 | 延后。 |

## AD-2.31 实施与验收边界

1. 只移动 `GmsInventoryDoc`，更新 `GmsInventoryDocMapper`、四个 GMS 消费者和受影响测试；不得移动 Mapper、
   Controller、DTO、其他 Entity、扫描根或跨域契约。
2. 保持 `@TableName("gms_inventory_doc")`、`BaseEntity`、历史 ID 行为、全部字段、遗留 Mapper 包、库存/成本/单据/
   明细/流水事务、FIN 快照口径、TRADE 库存命令、路由/DTO、表/Flyway/唯一键不变；不得新增跨域 Entity import 或
   修改 DDL。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；复核所有权登记、共享 import 报告、通配符与
   桥基线均没有意外扩大。

## 下一步

唯一下一最小任务为 **AD-2.31：迁移 `GmsInventoryDoc` 到 GMS 持久化实体包，并补 Mapper、库存单据、结账/退款与
财务快照回归。**
