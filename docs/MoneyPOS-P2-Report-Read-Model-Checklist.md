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
- [x] P2.3.2 已迁移 UMS 会员资产快照：替换会员充值、正余额总额和资产本金/赠金读取，并消除 FIN→UMS 实现依赖；保留原跨租户资产构成与逻辑删除过滤口径。
- [x] P2.3.3 已迁移 TRADE 订单/支付快照：替换当日核心指标、支付渠道、退款趋势、渠道优惠和今日订单资产概览读取；FIN 仅消费 API 不可变快照。
- [x] P2.3.4 已收敛 FIN 财务大盘组装并验收：删除遗留资产概览跨域 SQL；财务大盘仅依赖 API 快照，受控数据回归、全量测试、打包和架构门禁均已通过。
- [x] P2.4.0 已完成 FIN 专项报表盘点与首个查询契约设计：选择仅依赖 TRADE 订单审计投影的风控读面作为首个切片；详见 `MoneyPOS-P2.4-Finance-Specialist-Report-Read-Model-Design.md`。
- [x] P2.4.1 已迁移 FIN 风控审计快照：TRADE 提供收银员风险汇总与异常订单快照，FIN 保留日期解析、卡片聚合和既有 Map 响应字段。
- [x] P2.4.2 已迁移 FIN 交接班快照：TRADE 提供支付、优惠和品牌 ID 贡献快照，GMS 负责品牌名称翻译；FIN 保留既有金额分类、标签聚合、打印和响应字段。
- [x] P2.4.3 已迁移 FIN 利润排行与活动复盘：TRADE 分别提供近 30 天明细排行与近 3 个月活动聚合快照；FIN 保留 ROI 计算和排序。
- [x] P2.4.4.0 已完成经营分析、客流与利润审计盘点及首个契约设计：周期经营原子指标由 TRADE 所有；客流的 TRADE 数据与 SYS 策略读取分离，利润审计保留独立分页口径。
- [x] P2.4.4.1 已迁移 FIN 绩效报表与汇总卡片的 TRADE 周期经营原子指标：保留日/周/月分组、`PAID`/`PARTIAL_REFUNDED`、闭区间和 FIN 展示计算。
- [x] P2.4.4.2 已完成 FIN 销售看板查询切片设计：TRADE 提供商品、品牌 ID 数值和订单历史会员趋势，GMS 翻译品牌名称，FIN 保留补零与图表装配。
- [x] P2.4.4.2.1 已迁移 FIN 销售看板的 TRADE 商品、品牌与会员趋势快照：复用日周期指标，GMS 翻译品牌名称，FIN 保留图表补零与装配。
- [x] P2.4.4.3 已完成 FIN 客流查询切片设计：TRADE 小时/周/月聚合与 SYS 全局策略分离，FIN 保留默认窗口、除数、补零和阈值判定。
- [x] P2.4.4.3.1 已迁移 FIN 客流的 TRADE 数据与 SYS 策略快照：保持小时/周/月入口的默认范围、样本除数与阈值判定。
- [x] P2.4.4.4 已完成 FIN 品类销售与单品趋势查询切片设计：TRADE 事实、GMS 类目名称与 FIN 连续图表装配分离，保留各自退货公式。
- [x] P2.4.4.4.1 已迁移 FIN 品类销售与单品趋势的 TRADE/GMS 快照：保持各自退货公式、显示档案和时间范围。
- [x] P2.4.4.5 已迁移 FIN 利润审计：保持订单号/`ANOMALY` 筛选、状态集、分页和排序口径。
- [x] P2.4.5 已迁移瀑布流：分别收敛 TRADE/GMS 输入，FIN 仅组装既有公式；不把不同财务口径强行合并。
- [x] P2.5 已完成验收与复核：FIN/HOME 集成回归、全量 Maven 测试、构建和架构门禁均通过；剩余跨 Feature 实现导入与共享 Entity 归属已记录。详见 `MoneyPOS-P2-Final-Acceptance-Review.md`。

## 完成定义

每个切片必须同时满足：

1. 调用方只依赖 `money-app-api` 的报表快照/查询契约；
2. 查询所有者仍在拥有数据写入责任的 Feature 内部使用 Mapper/Entity；
3. 同时间范围、状态过滤、租户过滤和金额公式下，接口输出保持兼容；
4. 不新增跨 Feature 实现包 import，且全量验证通过。

## 当前最小任务

**P2 已完成。后续工作应作为独立架构债务任务处理：共享 Entity 物理归属、既有 TRADE → FIN 兼容调用，以及新跨域场景的窄契约。**
