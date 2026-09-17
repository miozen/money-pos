# MoneyPOS P2.0：FIN/HOME 报表读模型调用面基线

## 基线结论

FIN/HOME 当前以同步只读聚合为主，不参与订单、库存或会员资产写入；唯一写入是 HOME 对其自有 `OmsDailySummary` 日快照的补偿与更新。风险集中在调用方跨域直接使用 Mapper、Entity、`IService<Entity>` 或 Feature 实现服务，导致报表字段与底层表结构耦合。

P2.0 基线时架构扫描有 4 项跨 Feature 实现 import，其中 3 项属于 HOME→GMS，1 项属于 FIN→UMS。P2.1 已将 HOME→GMS 收敛为 `InventoryValuationQuery`，当前只剩 FIN→UMS 与 TRADE→FIN 两项；P2 继续按页面所需数据设计窄读快照，而不是简单删除依赖。

## HOME 调用面

| 调用方/接口 | 读取数据与当前实现 | 所有者 | 风险 | 建议切片 |
| --- | --- | --- | --- | --- |
| `HomeServiceImpl.homeCount()` | 已通过 `InventoryValuationQuery` 读取库存估值；`OmsOrderMapper` 对订单金额/成本聚合 | GMS、TRADE | 库存估值已收敛；订单聚合仍直接依赖 TRADE Entity/Mapper | 已完成 P2.1；P2.2 首页销售汇总 |
| `HomeServiceImpl.getChartsData()` / `GET /home/charts` | `OmsOrderDetailMapper` 的趋势、品牌饼图；`UmsMemberBrandLevelMapper` 的会员等级柱图 | TRADE、UMS | 图表 SQL 和 Mapper 直接泄露到 HOME | P2.2 HOME 图表快照 |
| `DecisionEngineServiceImpl.generateDailySnapshot()` | `OmsOrderAnalysisMapper`、`JdbcTemplate` 直读 `ums_member`、已通过 `InventoryValuationQuery` 读取库存估值，写 HOME 自有 `OmsDailySummary` | TRADE、UMS、GMS、HOME | 库存估值已收敛；订单/会员 SQL 仍在调用方硬编码 | 已完成 P2.1；P2.2 日快照输入 |
| `DecisionEngineServiceImpl.getTodayDashboardWithAlerts()` / `/home/count` | 读取 HOME `OmsDailySummary` 与其均值；调用补偿/生成日快照 | HOME | `OmsDailySummary` 是 HOME 归属读模型，保留写入 | P2.2 保持写时机，仅替换输入读取 |

## FIN 调用面

| 调用方/接口 | 读取数据与当前实现 | 所有者 | 风险 | 建议切片 |
| --- | --- | --- | --- | --- |
| `FinanceDashboardServiceImpl` / `/finance/dashboard`、`/channel-mix`、`/dashboard/asset` | TRADE 订单/支付 Mapper 与 `OmsOrder`；GMS 库存单据 Mapper 与 `GmsInventoryDoc`；UMS 会员流水 Mapper、`UmsMemberService.listObjs(UmsMember)`；`FinanceReportMapper` 直查订单/会员表 | TRADE、GMS、UMS | FIN→UMS 实现依赖，且多个跨域 Entity/Mapper 混合参与金额公式 | P2.3，按订单支付、库存、会员资产三组快照拆分 |
| `FinanceShiftServiceImpl` / `/finance/shift-handover` | 订单、支付、订单明细 Mapper 的班次支付、优惠、品牌贡献聚合 | TRADE | 班次口径与 TRADE 查询实现耦合 | P2.4 TRADE 班次快照 |
| `FinanceProfitServiceImpl`、`OmsSalesAnalysisServiceImpl` / 利润排行、活动复盘、经营分析 | TRADE 订单分析、明细、审计、客流 Mapper；SYS 策略 Mapper | TRADE、SYS | 报表 DTO 直接绑定订单分析 Mapper；SYS 阈值是独立参考数据 | P2.4 TRADE 经营分析快照；策略读取单独保留/收敛 |
| `FinanceRiskServiceImpl` / `/finance/risk-control` | `OmsOrderAuditMapper` 的收银员风险与异常订单 | TRADE | 风控指标直接绑定审计 SQL 返回 `Map` | P2.4 TRADE 风控快照 |
| `FinanceReportMapper` / 瀑布流 | 同一 SQL `UNION ALL` 直读 `oms_order`、`gms_inventory_doc`，资产构成直读 `ums_member` | TRADE、GMS、UMS | SQL 在 FIN 基础设施层混合多个数据所有者，变更任一表会影响报表 | P2.4 保持瀑布流公式，先明确所有者快照边界 |

## Entity 与 Mapper 归属摘要

| 归属 | P2 中被读取的关键类型 | 说明 |
| --- | --- | --- |
| TRADE | `OmsOrder`、订单/支付/明细/分析/审计/客流 Mapper | 订单金额、成本、支付渠道、退款、品牌销售和风控的权威来源。 |
| GMS | `GmsInventoryDoc`、库存单据 Mapper、`GmsGoodsService.getCurrentStockValue()` | 库存损耗/采购与当前库存估值的权威来源。 |
| UMS | `UmsMember`、`UmsMemberLog`、会员/等级 Mapper 与 `UmsMemberService` | 余额本金/赠金、充值流水和会员等级分布的权威来源。 |
| HOME | `OmsDailySummary`、其 Mapper | HOME 自有日快照；P2 只替换生成输入，不迁移其写入。 |
| FIN | `FinanceReportMapper` 与 FIN 输出 DTO | FIN 是读模型组装者，不应继续承担跨域表访问的长期所有者。 |

## 风险与约束

- 金额公式、订单状态过滤、查询时间边界和租户拦截是报表兼容面，快照必须逐项复用，不能仅按字段名重写。
- HOME 日快照补偿会在读取 `/home/count` 时执行；P2.2 前不得调整这一时机。
- 财务瀑布流是跨三个所有者的组合公式，不能作为首个迁移切片。
- 首个安全切片是库存估值：两个 HOME 调用点都只需要一个 `BigDecimal`，不需要商品 Entity、分页或写操作。

## 下一最小任务

**P2.2：将 HOME 的订单汇总、趋势、品牌分布和会员等级图表按 TRADE/UMS 所有者划分为只读快照。**
