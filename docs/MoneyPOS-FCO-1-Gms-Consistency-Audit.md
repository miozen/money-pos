# MoneyPOS FCO-1：GMS 一致性审查记录

> 状态：**只读审查完成，未实施优化**；审查日期：2026-10-15；适用分支：`dev`。
> 上位契约：`MoneyPOS-Feature-Consistency-Audit-and-Optimization-Implementation-Contract.md`。

## 1. 审查范围和结论

本记录覆盖 GMS 的商品、品牌、分类、价格矩阵、套餐、库存命令、库存单据、库存流水、库存分析与周转预警，
以及它向 UMS、TRADE、FIN、HOME 暴露的契约。

结论：GMS 已是商品和库存的明确数据所有者；与其他 Feature 的协作均使用 `money-app-api` 的 Entity-free
契约，未发现跨 Feature Entity、Mapper 或实现类依赖。TRADE 的普通结算、退款和会员提货均由 GMS 保持库存写入
所有权，并在既有单数据库事务中协作。

同时确认四项一致性问题：周转 GET 写快照、手工库存单据出库的并发库存窗口、商品删除未维护商品计数且可能留下
套餐悬挂引用、若干 GMS 管理入口仅有认证而没有细粒度权限。它们应按下文候选逐项设计，不能合并为一次大重构。

## 2. 现状地图

### 2.1 数据所有权

| 子域 | 主数据/表 | 当前写入所有者 | 备注 |
| --- | --- | --- | --- |
| 商品目录 | `gms_goods`、`gms_brand`、`gms_goods_category` | GMS 商品、品牌、分类服务 | 商品保存时维护品牌/分类的 `goods_count`；删除路径例外见问题 GMS-3。 |
| 价格与套餐 | `pos_sku_level_price`、`gms_goods_combo` | `GmsGoodsPriceService`、`GmsGoodsComboService` | 商品应用服务在同一事务内协调主档、价格矩阵和套餐配方。 |
| 手工库存单据 | `gms_inventory_doc`、`gms_inventory_doc_item`、`gms_stock_log` | `GmsInventoryDocServiceImpl` | 后台的入库、盘点、报损入口均使用此路径。 |
| 交易库存命令 | `gms_goods.stock`、库存单据和流水 | `GmsStockCommandService` | TRADE 只能发送销售、退款、提货及提货退款命令。 |
| 历史库存订单 | `gms_inventory_order`、`gms_inventory_order_detail` | `GmsInventoryOrderServiceImpl` | 当前没有生产调用方，仅由独立集成测试覆盖；不得在无迁移设计时删除。 |
| 周转快照 | `gms_turnover_warning_snapshot` | `GmsTurnoverServiceImpl` | 当前由预警查询隐式写入，见问题 GMS-1。 |

表结构和历史数据由 `V1.0.0__init_database.sql` 建立；`V1.0.2` 增加周转快照，`V1.0.7` 扩展会员提货退回库存单据类型。
本审查不修改 Flyway 或历史数据。

### 2.2 HTTP 与应用调用链

| 入口类别 | 路由/服务 | 写入或读取路径 |
| --- | --- | --- |
| 商品、品牌、分类 | `/gms/goods`、`/gms/brand`、`/gms/goodsCategory` | Controller → 对应应用服务 → GMS Mapper/本域 Entity。 |
| 商品 Excel | `/gms/goods/import`、`/template`、`/export` | 导入由 `GmsGoodsExcelManager` 写入；模板/导出由 `GmsGoodsExcelReadService` 读取。 |
| 手工库存 | `/gms/inventory/inbound`、`/check`、`/outbound` | Controller 固定单据类型 → `GmsInventoryDocServiceImpl.executeDoc` 的本地事务。 |
| 库存分析 | `/gms/analysis/report`、`/export` | `GmsStockAnalysisServiceImpl` 基于库存流水聚合。 |
| 周转预警 | `/gms/analysis/turnover-*` | `GmsTurnoverServiceImpl` 读取策略和周转数据；预警 GET 额外写快照。 |

GMS Controller 不直接注入 Mapper；本轮架构扫描的 Controller→Mapper、跨 Feature Mapper/实现、共享 Entity 与
跨 Feature 实现 import 均为 0。

### 2.3 向其他 Feature 提供的契约

| 消费方 | GMS 契约 | 用途 |
| --- | --- | --- |
| TRADE | `CheckoutGoodsQuery`、`PosGoodsCatalogQuery`、`SaleStockCommandHandler`、`RefundStockCommandHandler`、`MemberPickupStockCommandHandler`、`MemberPickupReturnStockCommandHandler` | POS 搜索、结账价格/库存快照、销售扣库、退款/提货返库。 |
| UMS | `BrandNameQuery`、`BrandSelectionQuery`、`BrandPricingLevelQuery`、`GoodsNameQuery` | 会员权益显示、档位和金额套餐的品牌/价格等级校验。 |
| FIN | `FinanceInventoryDocumentQuery`、`FinanceWaterfallInventoryQuery` | 手工出库/盘点财务投影与入库采购瀑布图。 |
| HOME | `InventoryValuationQuery` | 当前库存成本货值。 |
| 兼容 POS | `LegacyPosGoodsSearchQuery` | 保留历史商品搜索路由的既有查询口径。 |

这些均只返回快照/命令 DTO；GMS 没有向其他 Feature 暴露持久化 Entity。

### 2.4 事务、一致性与幂等

- `GmsGoodsServiceImpl`、品牌/分类服务、Excel 导入、手工库存单据均以 Spring 事务执行；主档、价格矩阵、套餐
  配方和相应计数在其正常写入链内共同回滚。
- TRADE 的 `CheckoutOrchestrator` 和退款服务拥有跨 Feature 的总事务；GMS 的销售、退款、会员提货命令在该事务中
  用 `UPDATE ... WHERE stock >= qty` 原子扣减或原子加回库存，并写库存单据和流水。
- 普通结账以 TRADE 请求号幂等，退款使用退款幂等表，会员提货由其业务单据请求号幂等；GMS 不另行重放同一交易命令。
- 后台手工库存单据使用随机单据号且没有请求幂等键；属于人工操作路径，当前没有重复提交防重放语义。

### 2.5 测试证据与边界

已有隔离 MariaDB 集成测试覆盖商品/价格/套餐/入库、库存订单、库存日志、会员提货库存语义、Excel 读写、周转快照及
TRADE 的结算、退款、套餐库存传播。`CheckoutIntegrationTest` 还覆盖套餐结算和全额退款的物理库存回补。

本轮未重新执行 Maven 测试；上述通过记录来自仓库既有测试与接力文档，不能视为本轮重新验证。
本轮执行 `scripts/test-architecture-scan.sh`、`scripts/architecture-scan.sh --check-new` 和 `git diff --check`，均通过。

未见针对以下情况的直接测试：手工出库并发竞争、商品删除后的品牌/分类计数和套餐引用、周转 GET 无写入语义、
以及 GMS Excel/品牌策略/分析路由的权限契约。

## 3. 已确认约束

1. GMS 是商品、库存、库存单据和库存流水的唯一写入所有者；TRADE 不得直接写 GMS 表。
2. 结算、退款、会员提货的现有事务归 TRADE 所有；GMS 优化不能移动或拆分这些事务。
3. 库存领域 `RETURN`、`MEMBER_PICKUP` 与 `MEMBER_PICKUP_RETURN` 是独立库存类型码，不能与订单状态混淆或重写历史值。
4. 历史 POS 搜索契约刻意保留未分组的搜索谓词与字段口径；不得在“统一查询”名义下改变它。
5. `gms_inventory_order` 是已存在的兼容数据模型和 Spring 服务，即使目前无生产调用方，也不能以本审查直接删除。

## 4. 已确认问题

| 编号 | 影响 | 事实与证据 | 风险 |
| --- | --- | --- | --- |
| GMS-1 | 高 | `GmsTurnoverServiceImpl.getTurnoverWarnings()` 在 GET 查询结束前调用 `upsertTodaySnapshot`，并吞没所有写入异常；现有 `GmsTurnoverSnapshotIntegrationTest` 明确把该副作用作为行为断言。 | 查询请求、导出请求和浏览器刷新会改变 `gms_turnover_warning_snapshot`；并发读存在查询-插入竞态，写失败不可观测，违反本阶段“查询不写”的统一标准。 |
| GMS-2 | 高 | `GmsInventoryDocServiceImpl` 先读 `gms_goods.stock` 判断报损库存，再以无 `stock >= qty` 条件的 `stock = stock + changeQty` 更新；手工 `/gms/inventory/outbound` 正在使用此路径。 | 两个并发报损请求可都通过旧库存判断并把库存扣为负数；单据明细/流水的前后库存快照也可能与最终顺序不一致。 |
| GMS-3 | 中 | `GmsGoodsServiceImpl.delete` 仅删除主商品、价格矩阵和以该商品为套餐主件的配方，未递减品牌/分类 `goods_count`，也未处理其作为 `sub_goods_id` 被其他套餐引用的行。 | 分类删除依赖 `goods_count`，计数漂移会错误拒绝删除；删除套餐子件会留下配方悬挂引用，后续套餐扣库在 GMS 命令中失败。 |
| GMS-4 | 中 | `/gms/goods/import`、`/gms/brand/config` 的写入口，以及多个分析读取入口没有 `@PreAuthorize`；安全框架仍要求认证。 | 已登录但未被授予相应 GMS 功能权限的用户可调用这些入口，和其余 GMS CRUD/库存入口的细粒度权限模型不一致。 |

## 5. 最小优化候选（未授权实施）

### GMS-OPT-1：周转快照读写分离（对应 GMS-1）

将快照组装/写入提取为 GMS 内部命令，由显式的应用就绪/定时触发器执行；`/turnover-warnings`、趋势和导出保持只读。
保留既有周转计算、快照表、响应字段和 30 日趋势口径。需要先设计刷新频率、失败日志和并发唯一键冲突处理，再替换当前
以 GET 刷新快照的特征测试。

### GMS-OPT-2：手工报损原子库存守卫（对应 GMS-2）

在不改变 `/gms/inventory/outbound` 路由、单据结构和成本公式的前提下，将报损扣库收敛到已有的原子库存条件更新，
并增加并发回归：有限库存下只允许一个报损单成功。盘点“实际数量校正”和入库的业务语义不在该切片改变范围内。

### GMS-OPT-3：商品删除完整性守卫与计数回收（对应 GMS-3）

先冻结删除策略：至少应拒绝删除仍被套餐作为子件引用的商品，并在成功删除时同步递减原品牌/分类计数。是否还要拒绝删除
存在库存、库存单据、订单明细或会员权益历史关联的商品，是影响既有管理行为的业务决策，必须单独确认；不能擅自级联删除。

### GMS-OPT-4：GMS 管理入口权限对齐（对应 GMS-4）

为导入、品牌策略写入和分析读取补充与现有 GMS 权限树一致的 `@PreAuthorize`，并新增路由权限回归。具体复用
`gmsGoods:*`、`gmsBrand:*` 还是引入新权限会影响现有角色，应先盘点菜单/角色迁移，不在本阶段直接假设。

## 6. 建议的后续选择

优先进入 **GMS-OPT-1 的最小设计**：它不改变商品、库存、金额或公开响应，仅消除读侧写入和静默失败，且可沿用 HOME
已验证的“命令刷新 + GET 只读”模式。随后处理 GMS-OPT-2 的并发库存守卫。GMS-OPT-3、GMS-OPT-4 含管理权限或删除规则
取舍，应在具体实施前确认。
