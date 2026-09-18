# AD-2.8 第四个共享 Entity 物理归属切片选择

## 结论

第四个物理归属切片选择 **GMS 的 `GmsInventoryOrderDetail`**，并已由
**AD-2.9** 完成：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，并更新 GMS 库存单服务和
GMS 专用 Mapper 的导入，同时新增 Mapper CRUD 装配回归。

该 Entity 是库存单明细的 GMS 本域持久化记录，不是 HTTP、DTO、Excel 或跨 Feature 契约。AD-2.9 未移动
同一聚合的 `GmsInventoryOrder`：后者仍通过 `GmsInventoryOrderService extends IService<GmsInventoryOrder>`
构成兼容服务面，连带移动会超出一个 Entity 的物理归属切片。

## 已确认的消费面

| 面 | 当前事实 | AD-2.9 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsInventoryOrderDetail`；`IdType.ASSIGN_ID`，`Long` 的订单/商品/租户字段、数量、价格和创建时间 | 原样移至 GMS 持久化 entity 包。 |
| Mapper | `feature.gms.infrastructure.persistence.mapper.GmsInventoryOrderDetailMapper extends BaseMapper<GmsInventoryOrderDetail>` | Mapper 包、名称及泛型以外的定义不变；只更新导入。 |
| 应用消费者 | 仅 `feature.gms.application.inventory.GmsInventoryOrderServiceImpl`；采购入库、盘点和报损三条命令在事务内组装明细后插入 | 只更新导入；不改单号、均价、库存、日志、事务或插入顺序。 |
| HTTP/DTO/序列化 | Controller/服务命令接收的是 `GmsInventoryOrderDTO` / `GmsInventoryOrderDetailDTO`；未发现 Controller、API DTO、JSON、Excel 或跨 Feature 契约直接暴露 Entity | 不新增 DTO、桥或路由改动。 |
| Mapper XML / 资源 FQCN | Java 以外资源目录没有 resultMap、参数类型、别名或 FQCN 引用 | 不改 XML/资源。 |
| Spring 装配 | `MybatisConfig` 已显式扫描 `com.money.feature.gms.infrastructure.persistence.mapper` | 不加 `@Mapper`，不改扫描配置。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `gms_inventory_order_detail`：分配式主键，`order_id`、`goods_id`、`qty` 非空，`price`、`create_time`、`tenant_id` 保留既有默认/索引语义 | 不改表、索引或 Flyway。 |

实体没有显式 `@TableName`，当前依赖 MyBatis-Plus 的驼峰命名推导到
`gms_inventory_order_detail`。这不是迁移时补注解的授权：AD-2.9 已保持该注解缺席，直接 Mapper 回归用于
锁定现有表名推导、字段映射、分配式主键与租户字段行为。

此前仅有 API Entity 定义、GMS Mapper 和 GMS 服务三个生产源码命中；没有跨 Feature Entity 消费。迁移后
`GmsInventoryOrderServiceImpl` 不再导入共享 Entity，Feature 共享 Entity 文件数已由 **73** 降至 **72**，
所有权登记已由 26 降至 25；精确跨域桥与通配符基线均未变化。

## 必须补充的回归

此前没有库存单明细的专项测试。AD-2.9 已新增隔离 `money_pos_test` 的 Mapper 集成回归，直接经
`GmsInventoryOrderDetailMapper` 验证：

1. 插入 GMS 本地 Entity 后取得 `ASSIGN_ID` 主键，并能按主键读回 `orderId`、`goodsId`、`qty`、`price`、
   `createTime` 和 `tenantId`；测试须建立既有租户上下文，且不改变拦截器。
2. 更新数量或价格后读回正确，删除后不存在，锁定 `BaseMapper` 泛型、隐式表名、主键和字段映射。
3. 回归只验证既有持久化语义；不通过库存单服务重写、补测或修改采购/盘点/报损公式、库存更新、日志写入或
   事务边界。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `GmsInventoryOrderDetail` | GMS；GMS 库存单服务 + 已在 GMS 扫描根内的专用 Mapper | 单一实体、无跨域/公开契约；可用独立 CRUD 回归锁定隐式表名和持久化字段 | **选择**。 |
| `GmsInventoryOrder` | GMS；同一服务、Mapper 与 `IService<GmsInventoryOrder>` 接口 | 迁移会改变服务兼容泛型，可能扩大为 API/调用面改造 | 延后，不与明细切片混合。 |
| `GmsMemberTransaction` | UMS；仅遗留 `com.money.mapper` Mapper | 仍无应用消费者；实体无 `@TableName`、Mapper 无 `@Mapper`，需要先独立解释遗留扫描/推导的运行语义 | 延后。 |
| `GmsGoodsCombo` | GMS；遗留 Mapper 与商品服务 | 关联商品组合、库存和服务行为，消费者多于本轮 | 延后。 |
| `OmsDailySummary` | HOME；HOME 写入/查询/补偿与 Mapper | 已有行为保护但读模型写入面宽，不能与简单持久化迁移混合 | 延后。 |
| SYS / UMS 配置、资产 Entity | SYS / UMS；存在跨域桥、硬件或公开兼容面 | 需先做窄契约或模块边界设计 | 延后。 |

## AD-2.9 实施与验收结果

1. 仅移动 `GmsInventoryOrderDetail`，并更新 `GmsInventoryOrderDetailMapper` 与
   `GmsInventoryOrderServiceImpl` 导入；`GmsInventoryOrder`、其他 Entity、Mapper、Controller 与服务均未移动。
2. 无 `@TableName` 的现状、`IdType.ASSIGN_ID`、字段 Java 类型、GMS Mapper 包及 `MybatisConfig` 扫描根均保持不变；
   表/Flyway、HTTP、DTO、事务、租户拦截器、库存/均价/日志算法未改。
3. `rg` 已确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 文件扫描减少一项。
4. `GmsInventoryOrderDetailMapperIntegrationTest` 已覆盖插入生成主键、读回、更新和删除；专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check` 均通过。

## 下一步

唯一下一最小任务为 **AD-2.10：选择第五个共享 Entity 物理归属迁移切片**。
