# AD-2.6 第三个共享 Entity 物理归属切片选择

## 结论

第三个物理归属切片选择 **UMS 的 `PosMemberLevel`**。后续实施任务编号为 **AD-2.7**：仅将它从
`money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.ums.infrastructure.persistence.entity`，并将遗留
`com.money.mapper.PosMemberLevelMapper` 的泛型导入改为 UMS 本地实体，同时增加 Mapper CRUD 装配回归。

本轮不移动 Mapper 本身：它是 `com.money.mapper` 兼容扫描根的一部分，物理迁移会要求单独调整
`MybatisConfig` 的 UMS 扫描边界，超出当前“一个 Entity”的切片。该 Mapper 没有 Feature 应用消费者，保留其
包名和 Spring 装配，仅改泛型实体导入即可验证 Entity 物理归属。

## 已确认的消费面

| 面 | 当前事实 | AD-2.7 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.PosMemberLevel`，`@TableName("pos_member_level")`，自增 `id`、`levelName`、字符串 `tenantId` | 原样移至 UMS 持久化 entity 包。 |
| Mapper | 仅 `com.money.mapper.PosMemberLevelMapper extends BaseMapper<PosMemberLevel>`，已标注 `@Mapper` | 保留 Mapper 包、名称及兼容扫描根；只更新泛型导入。 |
| 应用/跨域消费者 | 未发现 Feature 应用服务、Controller、API DTO、JSON、Excel、`IService<Entity>` 或其他 Maven 模块引用 | 不新增兼容桥或 DTO。 |
| Mapper XML / 资源 FQCN | Java 以外的资源目录没有 resultMap、参数类型或 FQCN 引用 | 不改 XML/资源。 |
| Spring 装配 | `MybatisConfig` 已扫描 `com.money.mapper` | 不改扫描配置。 |
| 表/Flyway | `V1.0.0__init_database.sql` 的 `pos_member_level`，自增主键，`level_name` 与 `tenant_id` 均非空 | 不改表、索引或 Flyway。 |

`PosMemberLevel` 的唯一生产使用不位于 `feature/**`，所以 AD-2.7 后 Feature 共享 Entity 文件数预计仍为
**73**；所有权登记从 27 降至 26。这是符合门禁定义的正常结果，不能为了追求扫描数字连带迁移 Mapper。

## 必须补充的回归

当前没有该 Mapper 的专项测试。AD-2.7 必须新增隔离 `money_pos_test` 集成回归，直接经
`PosMemberLevelMapper` 验证：

1. 插入带 `levelName`、字符串 `tenantId` 的 UMS 本地实体会获得自增主键并能按主键读回；
2. 更新等级名称后读回值正确，删除后不存在，锁定 `BaseMapper` 泛型、表名、主键和字段映射；
3. 该测试只验证既有持久化行为，不把 Entity 变成 Controller、DTO 或跨 Feature 契约。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 回归与风险 | 决定 |
| --- | --- | --- | --- |
| `PosMemberLevel` | UMS；一项带 `@Mapper` 的遗留 Mapper，实体有显式表名与自增主键 | 可用小型 CRUD 集成回归锁定现有唯一持久化语义 | **选择**。 |
| `GmsMemberTransaction` | UMS；一项遗留 Mapper | Entity 没有 `@TableName`，Mapper 也没有 `@Mapper`；当前无调用或回归，无法在不先解释 MyBatis 推导/扫描的情况下安全迁移 | 延后。 |
| `OmsDailySummary` | HOME；三项 HOME 应用服务、遗留 Mapper 与特征测试 | 涉及空读模型、原子写入、七日补偿和 Dashboard 内部 Entity 传递，消费面远大于本轮 | 延后为专门 HOME 切片。 |
| SYS 配置/打印/参考 Entity | SYS / 运行时或系统模块边界 | 涉及策略契约、硬件打印或 `money-app-system` 边界 | 不作为本轮 UMS 最小迁移。 |

## AD-2.7 实施与验收边界

1. 只移动 `PosMemberLevel`，更新 `PosMemberLevelMapper` 导入；不得移动 Mapper、其他 UMS Entity、Controller
   或服务。
2. 保留 `@TableName("pos_member_level")`、`IdType.AUTO`、字段 Java 类型以及 `com.money.mapper` 扫描根。
3. `rg` 确认 API 模块无旧 Entity、业务 Java/测试/资源无旧 FQCN；所有权登记减少一项，Feature 文件扫描可保持 73。
4. 运行新增 Mapper 专项、隔离 `money_pos_test` 全量测试、打包、脚本夹具、架构门禁和 `git diff --check`。

## 下一步

唯一下一最小任务为 **AD-2.7：迁移 `PosMemberLevel` 到 UMS 持久化实体包，并增加 Mapper CRUD 回归**。
