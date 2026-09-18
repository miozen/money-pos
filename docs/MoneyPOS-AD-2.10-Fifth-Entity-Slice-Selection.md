# AD-2.10 第五个共享 Entity 物理归属切片选择

## 结论

第五个物理归属切片选择 **GMS 的 `GmsInventoryDocItem`**。后续实施任务编号为
**AD-2.11**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，并更新两个 GMS 库存命令服务和遗留
`com.money.mapper.GmsInventoryDocItemMapper` 的导入；同一提交增加 Mapper CRUD 回归及库存单明细快照断言。

该 Entity 是 GMS 库存单的持久化快照记录，不是 HTTP、DTO、Excel 或跨 Feature 契约。AD-2.11 不移动主单
`GmsInventoryDoc`、遗留 Mapper、库存日志、商品、套餐、Controller 或服务；Mapper 继续由既有
`com.money.mapper` 兼容扫描根装配。

## 已确认的消费面

| 面 | 当前事实 | AD-2.11 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsInventoryDocItem`，显式 `@TableName("gms_inventory_doc_item")`，继承 `BaseEntity` 的 `ASSIGN_ID` 主键及审计字段 | 原样移至 GMS 持久化 entity 包。 |
| Mapper | 遗留 `com.money.mapper.GmsInventoryDocItemMapper extends BaseMapper<GmsInventoryDocItem>`，无自定义方法或 XML | 保留 Mapper 包、名称、扫描根和泛型以外定义；只更新导入。 |
| 应用消费者 | `GmsInventoryDocServiceImpl` 在采购入库/盘点/报损事务中组装并写入快照；`GmsStockCommandService` 在销售、退款和库存调整命令中写入快照 | 只更新两个导入；不改库存变化、均价、单据/日志写入顺序或事务语义。 |
| HTTP/DTO/序列化 | HTTP 和服务命令使用 `InventoryDocRequestDTO`、`StockMutationLine` 等 DTO；未发现 Controller、API DTO、JSON、Excel 或跨 Feature 契约直接暴露 Entity | 不新增 DTO、桥或路由改动。 |
| Mapper XML / 资源 FQCN | 资源目录无 resultMap、参数类型、别名或 FQCN 引用 | 不改 XML/资源。 |
| Spring 装配 | `MybatisConfig` 已扫描 `com.money.mapper`，因此 Mapper 无 `@Mapper` 仍受现有扫描配置装配 | 不加注解，不改扫描配置。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `gms_inventory_doc_item`：`id` 自增表列、`doc_no`/`goods_id`/`change_qty`/`cost_price` 非空、`idx_doc_no`，以及 `BaseEntity` 审计列和 `tenant_id` | 不改表、索引或 Flyway。 |

虽然物理表的 `id` 为自增列，Entity 继承的 `BaseEntity` 已使用 `IdType.ASSIGN_ID`；这是既有 MyBatis-Plus
持久化语义，AD-2.11 不得改为 `AUTO` 或修改基类。迁移需保留显式表名、全部快照字段、审计填充与租户行为。

当前生产源码命中为 API Entity、遗留 Mapper 及两个 GMS 服务；没有跨 Feature Entity 消费。迁移后两个 GMS
服务不再导入共享 Entity，Feature 共享 Entity 文件数预计由 **72** 降至 **70**，所有权登记由 25 降至 24；
精确跨域桥与通配符基线均不应变化。

## 必须补充的回归

现有 `GmsFeatureIntegrationTest` 已通过 `GmsInventoryDocService.executeDoc()` 验证入库会创建主单和更新商品
库存，但未断言 `gms_inventory_doc_item` 快照。AD-2.11 必须在隔离 `money_pos_test` 中：

1. 扩展该入库回归，经现有 Mapper 查询并断言一条明细的 `docNo`、商品 ID/名称/条码、变动数量、成本价、
   变动前/后库存及租户字段，保持已有主单、库存与事务口径；不重写库存公式。
2. 新增或复用独立 Mapper CRUD 集成回归，验证 GMS 本地 Entity 的 `ASSIGN_ID`、显式表名、继承审计字段和
   业务快照字段可插入读回、更新和删除；测试须建立现有租户与认证上下文，不改拦截器。
3. 直接 Mapper 回归只锁定既有持久化映射；不将 Entity 变成 Controller、DTO 或跨 Feature 契约，也不为遗留
   Mapper 包迁移补做扫描重构。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `GmsInventoryDocItem` | GMS；两个 GMS 库存命令服务 + 已扫描遗留 Mapper | 显式表名、单一表和既有入库集成回归提供可扩展的快照语义验证 | **选择**。 |
| `GmsInventoryDoc` | GMS；库存服务、遗留 Mapper 与 FIN 读取快照实现 | 主单被 FIN 查询端口使用，物理迁移会扩大为跨 Feature 查询实现的协调 | 延后。 |
| `GmsInventoryOrder` | GMS；服务、Mapper 与 `IService<GmsInventoryOrder>` 接口 | 服务兼容泛型仍是较宽公开面 | 延后。 |
| `GmsMemberTransaction` | UMS；仅遗留 Mapper | 无应用消费者，且 Mapper 扫描/隐式表名尚未独立特征化 | 延后。 |
| `GmsGoodsCombo` / `GmsStockLog` | GMS；商品、库存、结账/退款或遗留服务 | 直接参与套餐库存、交易路径或兼容桥，消费者更宽 | 延后。 |
| SYS / UMS / TRADE 其余 Entity | 多个所有者；包含桥、公开兼容面或宽交易读写 | 需要窄契约或更大行为回归 | 延后。 |

## AD-2.11 实施与验收边界

1. 只移动 `GmsInventoryDocItem`，并更新 `GmsInventoryDocItemMapper`、`GmsInventoryDocServiceImpl` 和
   `GmsStockCommandService` 导入；不得移动主单、其他 Entity、Mapper、Controller 或服务。
2. 保留 `@TableName("gms_inventory_doc_item")`、`BaseEntity` 继承、`ASSIGN_ID`、字段 Java 类型、遗留 Mapper 包
   及 `com.money.mapper` 扫描根；不改表/Flyway、HTTP、DTO、事务、租户、库存/均价/日志算法。
3. `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 文件扫描预期减少两项。
4. 运行新增/扩展库存单明细专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.11：迁移 `GmsInventoryDocItem` 到 GMS 持久化实体包，并增加库存单明细与 Mapper CRUD 回归**。
