# MoneyPOS P2：FIN/HOME 报表读模型收敛清单

## 目标与边界

P2 将 FIN 与 HOME 的跨域报表读取逐步替换为场景化只读快照。目标是让报表调用方不再直接依赖其他 Feature 的 Entity、Mapper 或实现服务，同时保持既有 SQL 口径、图表字段、时间范围和 HOME 日汇总写入时机。

P2 不移动表、Flyway、Entity 物理包或 API 路由；不把报表查询拆成分布式服务；不改变收银、库存、会员资产的事务边界。共享 Entity 数量继续仅作追踪，不作为完成标准。

## 实施顺序

- [x] P2.0 建立 FIN/HOME 报表读模型调用面基线，详见 `MoneyPOS-P2.0-Report-Read-Model-Inventory.md`。
- [x] P2.1 已迁移 HOME 库存估值：GMS 提供单值 `InventoryValuationQuery`，替换 `HomeServiceImpl` 与 `DecisionEngineServiceImpl` 的全部三处库存估值读取；验证首页 `/home/count`、HOME 日汇总和统计服务均保持相同库存金额及原有计算口径。
- [x] P2.2 已完成 HOME 销售/图表快照设计：TRADE 将提供按既有口径分开的首页区间订单汇总、日快照输入、综合大盘区间汇总、趋势和品牌营收只读模型；UMS 将提供会员等级分布快照；HOME 保留页面组装和 `OmsDailySummary` 写入。详见 `MoneyPOS-P2.2-Home-Read-Snapshot-Design.md`。
- [x] P2.2.1 已迁移服务级首页区间订单汇总：TRADE `HomeOrderReadQuery` 返回订单数、销售额、成本和利润快照；`HomeService.homeCount()` 不再依赖 `OmsOrder`、`QueryWrapper` 或 `OmsOrderMapper`，今日、月、年、总计的右开时间范围和现有金融有效状态集保持不变。
- [x] P2.2.2 已迁移 HOME 日快照和综合大盘订单读取：TRADE 提供日快照原子值与综合大盘区间快照；`DecisionEngineServiceImpl` 不再读取订单分析 Mapper 或内嵌订单 SQL，仍拥有 `OmsDailySummary` 写入、缺失日期补偿和告警时机。
- [x] P2.2.3 已迁移 HOME 销售趋势与品牌营收图表：TRADE `HomeOrderReadQuery` 提供趋势点和品牌营收快照；`HomeServiceImpl` 不再依赖订单明细 Mapper，保留 `today` 最近七天特例和既有时间范围转换。
- [x] P2.2.4 已迁移 HOME 会员等级图表：UMS `HomeMemberDistributionQuery` 返回有效会员的品牌名称、等级代码和人数；HOME 不再依赖 UMS Mapper，品牌名称经既有 GMS `BrandNameQuery` 窄查询转换。
- [x] P2.2.5 已完成 HOME 报表快照验收：`/home/count` 与 `/home/charts` 控制器回归覆盖，HOME 集成回归、全量测试、打包和架构门禁均通过；路由、页面字段及 HOME 日快照写入/补偿时机未改变。
- [x] P2.3 已完成 FIN 财务大盘读模型盘点与快照设计：固定订单/支付、库存单据、会员资产及资产概览的公式、状态和日边界，并拆为三个数据所有者契约；详见 `MoneyPOS-P2.3-Finance-Dashboard-Read-Snapshot-Design.md`。
- [x] P2.3.1 已迁移 GMS 库存单据快照：`FinanceInventoryDocumentQuery` 返回类型与总金额，FIN 不再依赖库存单据 Mapper/Entity；负数单据扣减毛利、正数盘点与入库单不影响该扣减的回归已通过。
- [ ] P2.3.2 迁移 UMS 会员资产快照：替换会员充值、正余额总额和资产本金/赠金读取，并消除 FIN→UMS 实现依赖。
- [ ] P2.3.3 迁移 TRADE 订单/支付快照：替换当日核心指标、支付渠道、退款趋势、渠道优惠和今日订单资产概览读取。
- [ ] P2.3.4 收敛 FIN 组装并验收：移除财务大盘服务的跨域 Mapper/Entity 依赖，完成受控数据回归、全量测试、打包和架构门禁。
- [ ] P2.4 迁移 FIN 专项报表：交接班、利润、营销复盘、风控、经营分析与瀑布流 SQL 分别收敛为 TRADE/GMS/UMS 所有者查询；不把不同财务口径强行合并。
- [ ] P2.5 验收与复核：增加 FIN/HOME 集成回归，运行全量 Maven 测试、构建和架构门禁；复核剩余跨 Feature 实现导入与共享 Entity 归属。

## 完成定义

每个切片必须同时满足：

1. 调用方只依赖 `money-app-api` 的报表快照/查询契约；
2. 查询所有者仍在拥有数据写入责任的 Feature 内部使用 Mapper/Entity；
3. 同时间范围、状态过滤、租户过滤和金额公式下，接口输出保持兼容；
4. 不新增跨 Feature 实现包 import，且全量验证通过。

## 当前最小任务

**P2.3.2：迁移 UMS 会员资产快照，收敛充值、余额总额和资产构成读取。**
