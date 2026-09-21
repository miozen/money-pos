# AD-2.32 第十六个共享 Entity 物理归属迁移切片选择

## 结论

第十六个物理归属切片选择 **GMS 的 `PosSkuLevelPrice`**。后续实施任务编号为
**AD-2.33**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.gms.infrastructure.persistence.entity`，更新遗留
`PosSkuLevelPriceMapper`、七个 GMS 商品域消费者和受影响测试导入，并增加价格矩阵 Mapper 持久化回归。

这是 AD-2.31 之后 14 个已登记共享 Entity 中，不含生产跨 Feature Entity 契约、HTTP Entity 输出、资源 FQCN 或
复杂库存/会员/订单事务的最小真实所有者切片。`pos_sku_level_price` 的每一项数据均由 GMS 商品 SKU、会员等级
和会员价/券额组成；它不是 UMS 的会员资产，也不是 TRADE 的结账输入。TRADE 和遗留 POS 只获得 GMS 提供的
`CheckoutGoodsSnapshot`、`PosGoodsCatalogSnapshot` 或 `LegacyPosGoodsSearchSnapshot`，其中价格矩阵已经是
`Map<String, BigDecimal>`，不含持久化 Entity。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.33 边界 |
| --- | --- | --- |
| Entity | API 包中的 `PosSkuLevelPrice` 显式 `@TableName("pos_sku_level_price")`，`@TableId(IdType.AUTO)`，字段为 `id`、`skuId`、`levelId`、`memberPrice`、`memberCoupon`、字符串 `tenantId` | 原样移至 GMS 持久化 entity 包；不得更改表名、ID 策略、字段、Java 类型、空值语义或 Lombok 模型。 |
| 表 | Flyway `pos_sku_level_price` 使用 AUTO_INCREMENT 主键；`sku_id`、`level_id`、`member_price` 为 NOT NULL，`member_coupon` 默认 `0.00`，`tenant_id` 可空；没有唯一索引 | 保持历史 DDL、默认值、无唯一索引语义、Flyway 和已有数据；不得借迁移添加 `(sku_id, level_id)` 约束或改租户字段类型。 |
| Mapper | 遗留 `com.money.mapper.PosSkuLevelPriceMapper extends BaseMapper<PosSkuLevelPrice>`，仅有 `@Mapper`，无自定义 SQL/XML | Mapper 包、名称和扫描根不动；只更新泛型导入。 |
| 价格矩阵写侧 | `GmsGoodsPriceService.saveLevelPrices` 按 SKU 查询既有等级，删除不再提交的等级，并对保留等级更新价格/券额、对新等级插入；`GmsGoodsExcelManager` 有同等 Excel 导入写入路径 | 保留合并/删除/插入顺序、空价格跳过和空券额归零行为；不改 Excel 列、字典映射、商品导入事务或 SYS 品牌配置读取。 |
| GMS 商品读侧 | `GmsGoodsServiceImpl`、`GmsGoodsExcelReadService`、`CheckoutGoodsQueryService`、`PosGoodsCatalogQueryService` 与 `LegacyPosGoodsSearchQueryService` 均经 `GmsGoodsPriceService.getPriceMap` 把实体投影为现有 VO 或 Entity-free 快照 | 只更新本域类型导入和泛型/Lambda 引用；保持分页商品详情、Excel 导出、结账商品快照、POS 目录与旧 POS 搜索的价格/券额、空值、排序及关键字语义。 |
| HTTP / 跨域 | 没有 Controller 直接收发该 Entity；API 模块仅定义该 Entity 本身。TRADE、POS 和其他 Feature 不导入它，且现有商品查询契约不返回它 | 不新增 API 兼容副本、跨 Feature Entity import、Mapper 暴露或 DTO 改写。 |
| 通配符 | `GmsGoodsExcelManager` 的既有 `com.money.entity.*` 通配符同时用于 GMS 商品/分类/品牌和已登记 SYS 配置；该文件是既有通配符基线 | 为已迁移的 `PosSkuLevelPrice` 补 GMS 本地显式 import，但不移动或扩展该通配符，不改变跨所有者 SYS 桥。 |
| 测试 | `GmsFeatureIntegrationTest` 断言商品创建后的等级价/券额和结账快照；`GmsGoodsExcelReadServiceIntegrationTest` 验证动态会员价列和导出值；`GoodsPosFacadeIntegrationTest` 以夹具写入等级价并验证旧 POS 查询 | 更新本地类型导入，并增加 Mapper CRUD/读回回归；保持现有商品、Excel、结账和旧 POS 快照断言。 |

生产显式导入消费者为 `GmsGoodsPriceService`、`GmsGoodsServiceImpl`、`GmsGoodsExcelReadService`、
`CheckoutGoodsQueryService`、`PosGoodsCatalogQueryService` 与 `LegacyPosGoodsSearchQueryService`，另有
`GmsGoodsExcelManager` 的已登记通配符消费者；加上遗留 Mapper，共八个生产位置。全量检索没有发现 API DTO、
Controller 签名、Mapper XML/resultMap、YAML、SQL 资源、其他 Maven 模块或跨 Feature 源码 FQCN 消费。

## AD-2.33 必须补充的回归

1. 新增 `PosSkuLevelPriceMapperIntegrationTest`，在隔离 `money_pos_test` 中使用 GMS 本地 Entity 插入、按 ID
   读回、更新并删除一行唯一 SKU/等级夹具；断言 AUTO ID、显式表映射、SKU、等级、会员价、会员券和字符串租户字段。
   测试不得变更 DDL、索引或替代 SQL。
2. 保留并更新 `GmsFeatureIntegrationTest`：商品创建后的等级价/券额持久化，以及 `CheckoutGoodsSnapshot` 的
   `levelPrices` / `levelCoupons` 映射不变。
3. 保留并更新 `GmsGoodsExcelReadServiceIntegrationTest`：动态会员价表头和导出单元格值不变；不把 Excel
   读侧重新接回共享 Entity。
4. 保留并更新 `GoodsPosFacadeIntegrationTest`：旧 POS 查询仍由 GMS Entity-free 快照获得每级价格与券额，
   不让 `GoodsPosFacade` 或 TRADE 获得持久化 Entity。
5. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 与 `git diff --check`；确认 API
   无旧 Entity，业务 Java/测试/资源无旧 FQCN，登记减少一项且桥/通配符没有扩大。

迁移后所有权登记预计由 **14** 降至 **13**。当前 60 个 Feature 文件导入共享 Entity；六个显式 GMS
消费者中只有 `GmsGoodsPriceService` 不再导入任何其他共享 Entity，故文件指标预计为 **59**。本域显式加通配符
使用预计由 **91** 降至 **84**；跨所有者桥（6）与通配符基线（3）必须不变。这些都是报告指标，最终以扫描结果为准。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `PosSkuLevelPrice` | GMS；一个 Mapper、七个本域商品价格/快照/Excel 消费者，无生产跨域 Entity 使用 | 需保留价格矩阵合并、Excel 和多种商品查询快照，但消费者均在同一商品域且已有回归 | **选择**。 |
| `GmsStockLog` | GMS；Mapper、历史 Service/Controller、库存命令、库存分析和查询服务 | HTTP Controller 和库存事务/日志语义较宽 | 延后。 |
| `GmsBrand`、`GmsGoodsCategory`、`GmsGoods` | GMS；目录 CRUD、商品 HTTP/Excel、库存、价格、查询和多项应用服务 | Controller 或核心商品/库存算法面显著更宽 | 延后。 |
| `SysBrandConfig`、`SysStrategy` | SYS；本域管理服务之外仍被 GMS 商品导入/价格配置或周转服务使用 | 已有 GMS→SYS 跨所有者实体桥，需先设计窄契约 | 延后。 |
| `PosCouponRule`、`PosMemberCoupon`、`UmsMember*` | UMS；会员资产、券、导入、画像、结算、退款和公开接口 | 工作流、资产事务或既有 TRADE 兼容桥较宽 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` | TRADE；结账、退款、订单详情、报表和打印 | 核心交易和历史兼容面最宽 | 延后。 |

## AD-2.33 实施与验收边界

1. 只移动 `PosSkuLevelPrice`，更新遗留 Mapper、七个 GMS 商品消费者及受影响测试；不得移动 Mapper、
   Controller、DTO、其他 Entity、扫描根或跨域契约。
2. 保持 `pos_sku_level_price`、AUTO ID、所有字段、价格矩阵合并/清理、商品/Excel/结账/POS 快照口径、
   既有通配符及其 SYS 桥、路由、表/Flyway 和索引不变；不得新增唯一键、DDL 或跨域 Entity import。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；复核所有权登记、共享 import 报告、
   通配符与桥基线均没有意外扩大。

## 下一步

唯一下一最小任务为 **AD-2.33：迁移 `PosSkuLevelPrice` 到 GMS 持久化实体包，并补 Mapper、商品价格矩阵、
Excel、结账与 POS 商品快照回归。**
