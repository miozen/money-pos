# AD-2.24.1 Provinces 只读映射修复设计

## 前置事实与结论

AD-2.25 的隔离库特征化证实，`Provinces` 不能在当前模型下直接迁包。MyBatis-Plus 为继承
`BaseEntity` 的 `BaseMapper<Provinces>` 生成了：

```sql
SELECT id, district_id, province, city, city_geocode, district, district_geocode,
       lon, lat, create_by, create_time, update_by, update_time
FROM provinces
```

Flyway `V1.0.0__init_database.sql` 的 `provinces` 只有八个地理列，隔离 `money_pos_test` 报
`Unknown column 'id' in 'SELECT'`。因此缓存初始化读不到种子数据，三个 `SelectVO` 接口也不能作为
迁包回归基线。

本设计固定采用 **真实表驱动的只读模型**，不为适配通用基类而改变静态行政区表：先通过
**AD-2.24.2** 实施只读映射修复，验证后再由 **AD-2.25** 做不携带行为变化的 SYS 物理归属迁移。

## AD-2.24.2 的范围

| 层 | 调整 | 固定不变 |
| --- | --- | --- |
| Entity | `Provinces` 暂留 `money-app-api: com.money.entity`，移除 `BaseEntity`，以 `@TableName("provinces")` 显式锁定表名，仅保留八个地理字段 | 类名、字段名/类型、`@Schema`、API 包位置（本任务不迁包） |
| Mapper | `ProvincesMapper` 不再继承 `BaseMapper`；改为单一只读方法，例如 `selectAll()`，以显式八列 `@Select` 查询及列别名映射 `Provinces` | Mapper 名称、`com.money.mapper` 包与既有扫描根 |
| 服务 | `ProvincesService` 不再继承 `IService`；`ProvincesServiceImpl` 不再继承 `ServiceImpl`，构造注入 Mapper 并用其一次性读出种子数据建缓存 | 三个公开 `List<SelectVO>` 方法、双重检查锁、首次全表读取、去重及插入顺序、缓存生命周期 |
| HTTP | 不改 `ProvincesController` | `/provinces`、`/cities?province=`、`/districts?city=` 路由、参数与 `SelectVO` 返回 |
| 数据库/租户 | 不改表、种子、Flyway 或 `application-money.yml` 的 `provinces` tenant-ignore | 表仍是无主键、无审计列的静态字典数据 |

这不是“加几个 `exist=false` 注解”的临时补丁：显式列 SQL 可阻止框架再次推导不存在的列；移除
`BaseMapper`、`IService` 和 `ServiceImpl` 的泛型继承，则从代码 API 消除 `insert`、`update`、`delete`
等不符合静态字典责任的伪写入入口。生产全量搜索已确认没有外部调用这些继承方法；Controller 仅依赖三个
自定义读方法。

## 显式读查询契约

Mapper 查询必须只投影并映射以下列：

`district_id`, `province`, `city`, `city_geocode`, `district`, `district_geocode`, `lon`, `lat`。

查询不带租户条件；这与既有 `tenant.ignore-table: provinces` 一致。除非现有服务已有显式排序，SQL 不新增
`ORDER BY`，以保留原有表读取顺序及 `LinkedHashSet` 缓存去重顺序。不得新增写 SQL、主键、审计字段、分页、
缓存刷新端点或跨 JVM 缓存。

## 必须补充的回归

AD-2.24.2 必须在隔离 `money_pos_test` 中通过：

1. Mapper 只读回归：读取 Flyway 北京样本 `district_id=110100`，断言全部八个字段；测试不得插入、更新或删除。
2. 服务/Controller 回归：`/provinces` 包含“北京市”，`/cities?province=北京市` 包含“北京市”，
   `/districts?city=北京市` 包含“北京”；所有元素均为 `SelectVO`。
3. 缓存回归：首次读取后重复调用返回同一已初始化列表，不再访问写 API；不要求跨 JVM 持久化。
4. 运行专项、隔离全量测试、打包、架构扫描夹具、`--check-new` 与 `git diff --check`。

## AD-2.25 的恢复边界

AD-2.24.2 闭环并推送后，AD-2.25 只把已经验证的、显式 `@TableName("provinces")` 的纯地理 Entity
从 API 包移动到 `com.money.feature.sys.infrastructure.persistence.entity`，更新 Mapper、服务接口、实现和测试
导入，并从所有权登记移除 `Provinces`。不再改 Mapper API、服务继承、查询 SQL、Controller、表、Flyway、
tenant-ignore 或缓存行为。

## 下一步

AD-2.24.2 已按本设计闭环，AD-2.25 亦已完成纯物理迁移；唯一下一最小任务由债务清单重新盘点后确定。
