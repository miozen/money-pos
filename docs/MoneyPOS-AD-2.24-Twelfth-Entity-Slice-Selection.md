# AD-2.24 第十二个共享 Entity 物理归属迁移切片选择

## 结论

第十二个物理归属切片选择 **SYS 的 `Provinces`**。后续实施任务编号为 **AD-2.25**：仅将它从
`money-app-api: com.money.entity` 移至新建的
`money-app-biz: com.money.feature.sys.infrastructure.persistence.entity`，并更新仍在遗留扫描根中的
`ProvincesMapper`、`ProvincesService` 及其实现。Controller 保持不动；迁移后继续由它通过 `SelectVO`
提供 `/provinces`、`/cities`、`/districts`。

这是当前 18 个已登记共享 Entity 中最小的真实所有者切片：实体的直接生产引用仅有 SYS 的 Mapper、
服务接口和实现，Controller 没有暴露 Entity，三个公开方法均返回 `List<SelectVO>`。服务接口虽保留
`IService<Provinces>`，但全量源码搜索没有其他 Feature、Maven 模块、DTO/VO、序列化、Excel、资源或
跨域契约引用该类型；泛型的实际范围仍限于 SYS 遗留服务层。

> **实施前置条件更新（AD-2.24.1）**：AD-2.25 的首次隔离库特征化发现继承 `BaseEntity` 的默认查询会读取
> `provinces` 中不存在的 `id`/审计列。该问题已拆为 AD-2.24.2；只读映射修复闭环前不得实施本迁包。修复后的
> AD-2.25 以 `MoneyPOS-AD-2.24.1-Provinces-Read-Only-Mapping-Repair-Design.md` 的显式只读模型为准。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.25 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.Provinces`；无 `@TableName`，继承 `BaseEntity`，声明八个省市区字段 | 原样移至 SYS 持久化 entity 包；不添加/删除注解、基类或字段，不改 Java 类型。 |
| Mapper | `com.money.mapper.ProvincesMapper extends BaseMapper<Provinces>`，无自定义 SQL、XML 或 Entity FQCN 资源引用 | Mapper 包、名称和既有 `com.money.mapper` 扫描根不动；只更新泛型导入。 |
| 服务 | `ProvincesService extends IService<Provinces>`；`ProvincesServiceImpl extends ServiceImpl<ProvincesMapper, Provinces>` | 只更新本域导入/泛型；不移动服务，不删改 `IService`，不新增跨 Feature 兼容桥。 |
| HTTP / 缓存 | `ProvincesController` 仅返回 `SelectVO`；服务首次调用时执行一次 `this.list()` 全表读取，以双重检查锁建立 JVM 省、市、区缓存，随后从内存读取 | 路由、参数、响应字段、去重/插入顺序、首次加载时机、锁与缓存生命周期均不改。 |
| 表 / Flyway / 租户 | `V1.0.0__init_database.sql` 创建并灌入 `provinces`；表仅有八个地理字段。`application-money.yml` 将该表列入 tenant ignore-table | 保持隐式 `provinces` 表名、全部种子数据、租户忽略和 Flyway 不变；不新建迁移。 |
| 历史映射风险 | `BaseEntity` 仍声明分配式 `id` 与四个审计字段，而初始化表未包含这些列；当前业务只读地理字段 | **不得**趁物理迁移“修复”基类、字段、表或 CRUD 语义。先以只读特征化确认既有 Mapper/服务的实际查询映射，再原样搬迁。 |

`Provinces` 没有显式 `@TableName`，故仍依赖 MyBatis-Plus 的驼峰命名推导到 `provinces`。其表结构与继承
字段的历史不对称是既有事实，不是 AD-2.25 的授权范围：不能通过增加列、修改 Flyway、去掉 `BaseEntity`、
补 `@TableField(exist = false)` 或补写入型 CRUD 来改变它。`provinces` 的 tenant-ignore 配置同样必须保持。

迁移后所有权登记预计由 **18** 降至 **17**，共享 Entity import 文件预计由 **62** 降至 **60**（Mapper 与
服务接口将离开共享包，服务实现仍会使用其他共享 Entity 时应以实际扫描为准）。这些均为报告指标；跨所有者桥
（6）和通配符基线（3）不得扩大。

## AD-2.25 必须补充的回归

1. 在隔离 `money_pos_test` 先增加只读 Mapper/服务特征化回归：以 Flyway 灌入的北京样本验证地理字段映射，并
   验证首次读取构建省、市、区 `SelectVO` 缓存、重复读取不改变结果。若此步骤暴露现存映射/表结构问题，停止
   将其包装成迁移修复，保留失败证据并另行编号处理。
2. 增加或扩展 Controller/服务回归，覆盖 `/provinces`、按“北京市”查 `/cities`、按“北京市”查
   `/districts` 的 `SelectVO` 契约；不返回 `Provinces`，不要求缓存跨 JVM 持久化。
3. 不要求 Mapper 插入、更新或删除 CRUD：该只读种子表没有 `BaseEntity` 的 `id`/审计列，新增写回归会擅自
   扩大并改变既有数据责任。也不通过测试插入/删除或改动全量种子数据。
4. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 与
   `git diff --check`；确认 API 模块不存在旧 Entity，业务 Java/测试/资源不存在旧 FQCN，登记减少一项。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `Provinces` | SYS；单 Mapper、本域 `IService` / 实现；Controller 只输出 `SelectVO` | 仅有历史只读映射和 JVM 缓存约束，均可由只读特征化锁定 | **选择**。 |
| `SysBrandConfig`、`SysPrintConfig`、`SysStrategy` | SYS；配置读取、打印运行能力或商品/策略兼容面 | 兼容桥、运行设备或跨域商品策略面更宽 | 延后。 |
| `UmsRechargeOrder`、`UmsMemberLog`、会员/券 Entity | UMS；充值/资产/日志工作流及 Controller | HTTP 或更宽服务、资产语义和兼容面 | 延后。 |
| `GmsInventoryDoc`、`GmsStockLog`、商品/价格/库存 Entity | GMS；库存算法、分析、查询、Excel 或 FIN 查询实现 | 多消费者、算法或跨 Feature 查询面更宽 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` 及剩余 TRADE Entity | TRADE；订单主流程、聚合、退款/支付关联 | 交易事务和公开订单兼容面更宽 | 延后。 |

## AD-2.25 实施与验收边界

1. 只移动 `Provinces`，更新 `ProvincesMapper`、`ProvincesService`、`ProvincesServiceImpl` 及为本回归新增的
   测试导入；不得移动 Mapper、Controller、DTO、其他 Entity 或扫描根。
2. 保持无显式表名、`BaseEntity`、八个地理字段、遗留 Mapper 包、`provinces` tenant-ignore、Flyway 数据、
   HTTP 路由和缓存/去重行为；不得把选择任务识别到的历史映射风险混入修复。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；复核所有权登记、共享 import 报告、
   通配符与桥基线均没有意外扩大。

## 下一步

AD-2.25 已在 AD-2.24.2 只读映射修复后闭环：`Provinces` 已移至
`com.money.feature.sys.infrastructure.persistence.entity`，旧 API 副本与所有权登记均已删除，Mapper/缓存/接口
回归继续通过。下一最小任务由债务清单重新盘点后确定。
