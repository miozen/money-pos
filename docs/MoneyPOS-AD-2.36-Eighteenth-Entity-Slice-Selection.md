# AD-2.36 第十八个共享 Entity 物理归属迁移切片选择

## 结论

第十八个物理归属切片选择 **GMS 的 `GmsGoodsCategory`**。后续实施任务编号为
**AD-2.37**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，更新 GMS 分类 Mapper、分类服务接口/实现、
名称查询、商品结账/Excel 消费者和受影响测试，并增加分类 Mapper 持久化回归。

它是余下 12 个已登记共享 Entity 中生产面最小且无生产跨 Feature Entity 外泄的真实 GMS 所有者：分类仅提供本域
树形维护、商品数守卫及分类 ID→名称读取。TRADE 结账通过既有 GMS `CheckoutGoodsSnapshot` 获取分类名；FIN
报表通过既有 `GoodsCategoryNameQuery` 获取名称，均不取得实体。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.37 边界 |
| --- | --- | --- |
| Entity / 表 | API 包的 `GmsGoodsCategory extends BaseEntity` 显式映射 `gms_goods_category`；字段 `pid`、`icon`、`name`、`goodsCount`、`tenantId`，继承 ID、创建/更新人及时间 | 原样迁至 GMS 持久化 entity 包；不得改变表名、`BaseEntity` / ASSIGN_ID 行为、字段、Java 类型、Swagger 注解、审计或租户语义。 |
| DDL | Flyway 的 ID 为非 AUTO 的 `bigint unsigned`，`pid`、图标、名称、商品数、四个审计列及租户列均为非空；`icon` 默认空串、`goods_count`/租户默认 0 | 不改 ID 分配、默认值、审计列、表、Flyway 或已有数据；不得新增索引、软删除列或迁移数据。 |
| Mapper | `feature.gms.infrastructure.persistence.mapper.GmsGoodsCategoryMapper extends BaseMapper<GmsGoodsCategory>`；Mapper 已在 GMS 包，名称和扫描位置稳定 | 仅更新泛型导入；不得移动 Mapper、修改扫描、增加 XML 或 SQL。 |
| 分类写侧 | `GmsGoodsCategoryService extends IService<GmsGoodsCategory>`，实现负责同名校验、图标上传后的回滚清理、更新提交后的旧图清理、子分类/商品数删除守卫、树构建及原子 `goods_count` 增减 | `IService` 泛型仍是 GMS 本域类型；不得改变事务、OSS 时序、同名/子节点/有商品删除失败、树形、递归、商品数或异常语义。 |
| GMS 读侧 | `GoodsCategoryNameQueryService` 以 Mapper 批量读取并返回 ID→名称 Map；`CheckoutGoodsQueryService` 将分类名写入稳定的 `CheckoutGoodsSnapshot`；商品 Excel 导入/导出通过服务 `list()` 读取分类 | 只更新本域类型导入与 Lambda/泛型；保持空值、ID 解析、Map/快照、Excel 分类表头/下拉/导出名称及导入按名称匹配口径。 |
| HTTP / 契约 | 分类 Controller 只接收 DTO、返回 `SelectVO`/`TreeNodeVO`，不返回 Entity；FIN 名称查询和 TRADE 结账都使用 Entity-free 契约 | 不改路由、鉴权、DTO、响应字段、Mapper/Controller 包或跨域契约。 |
| 测试 / 资源 | 生产资源中无 Entity FQCN；GMS 核心、商品 Excel 与 FIN 测试只将其作为本地夹具，未形成生产外泄 | 测试夹具同步使用 GMS 本地类型；不得新增 API 副本、跨域 Entity import、桥或通配符。 |

生产直接 Entity 消费者为 GMS Mapper、`GmsGoodsCategoryService`、`GmsGoodsCategoryServiceImpl`、
`GoodsCategoryNameQueryService`、`CheckoutGoodsQueryService`、`GmsGoodsExcelReadService` 及既有通配符消费者
`GmsGoodsExcelManager`。没有生产跨 Feature Entity 或 Mapper 消费；分类 Controller 不导入 Entity。

## AD-2.37 必须补充的回归

1. 新增 `GmsGoodsCategoryMapperIntegrationTest`，在隔离 `money_pos_test` 直接用 GMS 本地 Entity 写入、读回、
   更新并删除唯一分类夹具，断言非 AUTO ID 的既有分配语义、全部业务字段、继承审计字段、租户及图标/商品数默认或显式值。
2. 保留并扩展 `GmsFeatureIntegrationTest`：锁定分类选择器、分类树/子 ID、商品数增减，以及子分类或商品数非零时
   的删除守卫；图标 OSS 提交/回滚时序保持既有实现，不用包迁移改变其行为。
3. 保留 `GmsGoodsExcelReadServiceIntegrationTest`：商品 Excel 的分类下拉、导出分类名称及按名称导入匹配不变。
4. 保留 `FinanceFeatureIntegrationTest`：FIN 继续经 `GoodsCategoryNameQuery` 取得分类名，而不是 Entity；TRADE
   结账覆盖仍经 `CheckoutGoodsSnapshot` 的分类名字段，不新增跨域 Entity import。
5. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 与 `git diff --check`；确认 API、
   业务 Java/测试/资源无旧 FQCN，登记减少一项且桥/通配符不扩大。

迁移后所有权登记预计由 **12** 降至 **11**。Feature 共享 Entity import 文件预计由 **59** 降至 **56**：分类服务接口、
实现和名称查询将不再导入其他共享 Entity；商品结账/Excel 消费者仍有其他共享 Entity，现有 Excel 通配符也必须保留。
owner-local uses 预计由 **79** 降至 **73**；跨所有者桥（6）与通配符基线（3）必须不变。均为报告指标，最终以扫描为准。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `GmsGoodsCategory` | GMS；一个本域 Mapper、分类服务/名称查询、结账与 Excel 读取；无生产跨域 Entity 使用 | 须锁定 `BaseEntity` ID、图标事务、树/删除守卫、商品/Excel 快照 | **选择**。 |
| `GmsBrand`、`GmsGoods`、`GmsStockLog` | GMS；品牌/商品 HTTP 与核心目录或库存命令、流水查询/旧服务兼容面 | Controller、库存/商品算法或兼容面更宽 | 延后。 |
| `UmsMember`、`UmsMemberLog`、`PosMemberCoupon`、`PosCouponRule` | UMS；会员主档、资产、券、结算/退款或 TRADE 兼容桥 | 事务、公开接口或跨所有者桥较宽 | 延后。 |
| `SysBrandConfig`、`SysStrategy` | SYS；仍被 GMS 直接读取 | 已登记 GMS→SYS Entity 桥，须先收敛为窄契约 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` | TRADE；结账、退款、查询、报表、打印 | 核心交易与兼容面最宽 | 延后。 |

## AD-2.37 实施与验收边界

1. 只移动 `GmsGoodsCategory`，更新上述 Mapper、GMS 本域消费者和受影响测试；不得移动 Mapper、Controller、DTO、
   其他 Entity、扫描根或跨域契约。
2. 保持显式表名、`BaseEntity` / ID、字段、审计/租户、图标事务、删除守卫、树/递归、商品数、名称查询、Excel、
   结账/FIN 快照、路由、表/Flyway 不变；不得新增跨域 Entity import 或 DDL。
3. 使用 `rg` 确认无旧 FQCN，并复核所有权登记、共享 import、通配符和桥基线没有意外扩大。

## 下一步

唯一下一最小任务为 **AD-2.37：迁移 `GmsGoodsCategory` 到 GMS 持久化实体包，并补 Mapper、分类树/守卫、
商品 Excel、结账与 FIN 分类名称快照回归。**
