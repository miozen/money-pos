# AD-2.12 第六个共享 Entity 物理归属迁移切片选择

## 结论

第六个物理归属切片选择 **UMS 的 `GmsMemberTransaction`**。后续实施任务编号为
**AD-2.13**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.ums.infrastructure.persistence.entity`，并更新遗留
`com.money.mapper.GmsMemberTransactionMapper` 的泛型导入；同一提交新增该 Mapper 的 CRUD
集成回归。

`GmsMemberTransaction` 的历史名称与物理表 `gms_member_transaction` 不改变它的逻辑归属：表字段是
会员 ID、充值/消费/退款/导入类型、金额、余额快照和关联单号，所有权登记为 UMS。AD-2.13 只收敛 Java
持久化类型的物理位置；不得重命名类、表、Mapper 或引入新的会员资金记账功能。

## 已确认的消费面

| 面 | 当前事实 | AD-2.13 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.GmsMemberTransaction`；直接声明 `@TableId(type = IdType.ASSIGN_ID)`，包含会员、金额、余额快照、单号、备注、创建时间和租户字段，不继承 `BaseEntity` | 原样移至 UMS 持久化 entity 包；不补 `BaseEntity`、审计填充或新注解。 |
| Mapper | 遗留 `com.money.mapper.GmsMemberTransactionMapper extends BaseMapper<GmsMemberTransaction>`，无自定义方法或 XML | 保留 Mapper 包、名称、扫描根和泛型外的定义；只更新导入。 |
| 应用消费者 | `rg` 仅命中 API Entity 和上述 Mapper；未发现 GMS、UMS、TRADE、FIN、HOME 服务或测试之外的实际应用消费者 | 不新增服务、命令、查询、调用方或桥接契约。 |
| HTTP/DTO/序列化 | 未发现 Controller、API DTO、JSON、Excel、其他 Maven 模块或跨 Feature 契约直接暴露该 Entity | 不改路由、响应、DTO 或序列化。 |
| Mapper XML / 资源 FQCN | 资源目录没有对应 XML、resultMap、参数类型、别名或旧 FQCN 引用 | 不改资源。 |
| Spring 装配 | `MybatisConfig` 已扫描 `com.money.mapper`；Mapper 无 `@Mapper` 但沿用现有扫描装配 | 不加注解，不改扫描配置。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `gms_member_transaction`：分配式 `id`、非空 `member_id`/`type`/`amount`/`balance_after`、`create_time` 与 `tenant_id` 默认值，以及会员和时间索引 | 不改表、索引、记录、Flyway 或物理表名。 |

实体没有显式 `@TableName`，现状依赖 MyBatis-Plus 的驼峰命名推导到
`gms_member_transaction`。这不是 AD-2.13 补注解、重命名表或改变默认值的授权；直接 Mapper 回归只用于锁定
既有隐式映射、`ASSIGN_ID`、字段类型和租户字段行为。由于当前没有 Feature 应用层导入它，迁移后共享 Entity
Feature 文件数预计仍为 **72**；所有权登记由 **24** 降至 **23**，跨域桥和通配符基线不变。

## 必须补充的回归

现有测试没有此 Entity 的行为覆盖。AD-2.13 必须在隔离 `money_pos_test` 中新增 Mapper 集成回归：

1. 建立既有认证和 `Y-tenant: 0` 上下文，插入 UMS 本地 Entity，断言生成 `ASSIGN_ID`，并按主键读回
   `memberId`、`type`、`amount`、`balanceAfter`、`orderNo`、`remark`、显式赋值的 `createTime` 与 `tenantId`。
2. 更新金额、余额快照或备注后读回正确，删除后不存在，锁定 `BaseMapper` 泛型、隐式表名和全部资金快照字段。
3. 测试只验证既有持久化映射；不创建会员资产、充值、结账、退款或导入业务数据流，也不改变事务、租户拦截器或
   资金计算口径。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `GmsMemberTransaction` | UMS；仅遗留 Mapper，无应用消费者 | 单一 Mapper、无公开或跨域面；本轮已完成表名推导、扫描根、字段和 Flyway 特征化，可由独立 CRUD 回归锁定 | **选择**。 |
| `GmsInventoryOrder` | GMS；Mapper、库存服务及 `IService<GmsInventoryOrder>` | 迁移会改变公开服务泛型，超出单 Entity 物理归属切片 | 延后。 |
| `GmsInventoryDoc` | GMS；库存服务、Mapper 及 FIN 查询实现 | FIN 读模型实现仍依赖 Entity，需跨 Feature 协调 | 延后。 |
| `GmsGoodsCombo` / `GmsStockLog` | GMS；商品、库存、结账/退款、分析或遗留服务 | 消费者和交易路径较宽，需要更大的行为回归 | 延后。 |
| `Provinces` | SYS；Mapper、`IService<Provinces>`、服务与 Controller | 兼容服务泛型和 HTTP 查询面均在范围内 | 延后。 |
| `OmsDailySummary` 及其余 UMS/TRADE/SYS Entity | 多个所有者；读模型、交易、配置或既有桥 | 存在更宽的命令/查询、公开兼容或跨域风险 | 延后。 |

## AD-2.13 实施与验收边界

1. 只移动 `GmsMemberTransaction`，并更新 `GmsMemberTransactionMapper`；不得移动 Mapper、Controller、服务、其他 Entity 或任何业务逻辑。
2. 保留 `@TableId(IdType.ASSIGN_ID)`、字段 Java 类型、没有 `@TableName`/`BaseEntity` 的现状、遗留 Mapper 包及 `com.money.mapper` 扫描根；不改表/Flyway、HTTP、DTO、事务、租户或会员资金规则。
3. `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 共享 Entity 文件报告数预期不变。
4. 运行新增 Mapper 专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.13：迁移 `GmsMemberTransaction` 到 UMS 持久化实体包，并增加 Mapper CRUD 回归**。
