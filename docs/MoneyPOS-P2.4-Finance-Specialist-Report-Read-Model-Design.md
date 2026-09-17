# MoneyPOS P2.4：FIN 专项报表读模型设计

## 目的与边界

P2.4 将 FIN 专项报表对 TRADE、GMS、UMS 的直接 Mapper/SQL 读取逐项迁移为数据所有者提供的窄查询契约。FIN 仍负责 HTTP 参数解析、展示 DTO/Map 组装、排序、图表补零及打印调用；数据所有者仍在自身 Feature 内保留 Mapper、Entity 和 SQL。

本阶段不修改路由、权限、页面字段、数据库表、Flyway、事务边界或打印协议；不把不同报表的状态集、日边界或金额公式抽成一个通用“财务报表”查询。所有新增 `money-app-api` 契约都必须是 Java 8 兼容的普通不可变类，不能暴露 Entity、Mapper、Spring 或通用 `Map`。

## 当前专项报表盘点

| FIN 入口 | 现有直接读面 | 数据所有者 | 现有关键口径 | 拟定切片 |
| --- | --- | --- | --- | --- |
| `GET /finance/risk-control` | `OmsOrderAuditMapper.getCashierRiskSummary`、`getAbnormalOrderList` | TRADE | 默认近 7 个日历日；日期参数以 `00:00:00` / `23:59:59.999999999` 闭区间解析；SQL 不额外过滤订单状态 | **P2.4.1 首个实施切片** |
| `GET /finance/shift-handover` 与打印入口 | 订单、支付、订单明细三个 Mapper | TRADE（品牌显示需明确 GMS 归属） | 指定班次起点到当前时刻闭区间；可选收银员；支付使用全额退款为零的 `net_amount` 回退公式；退款、优惠、品牌贡献各有独立公式 | P2.4.2，单独设计 |
| `GET /finance/profit-ranking` | `OmsOrderDetailMapper.getProfitRankingData` | TRADE | 最近 30 天起点至今；状态含 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`；按退货后的明细销量/利润排行 | P2.4.3a |
| `GET /finance/campaign-review` | `OmsOrderAnalysisMapper.getMarketingRoiStats` | TRADE | 最近 3 个月闭区间；仅 `PAID`、`PARTIAL_REFUNDED`；满减券与会员券是两条独立聚合口径，FIN 再计算并排序 ROI | P2.4.3b，不与排行合并 |
| 经营分析、绩效、营销、客流、品类、单品趋势、利润审计 | 订单分析、客流、审计 Mapper，另有 SYS 策略 Mapper | TRADE、SYS；品牌/品类 SQL join 需标注 GMS 归属 | 每个入口的默认范围、状态集、分组维度和前端补零规则不同 | P2.4.4，按入口拆分 |
| `GET /finance/report/waterfall/daily` | FIN 内 `FinanceReportMapper` 的 `oms_order` / `gms_inventory_doc` UNION ALL | TRADE、GMS | 金融有效订单的应收、优惠、支付、退款、净收与入库采购额按日 UNION；空查询对象短路 | P2.4.5，最后拆解多所有者组合 |

## P2.4.1：TRADE 收银风控查询契约

选择风控作为第一个实现切片，原因是它只读取 `oms_order` 审计投影、无跨所有者 join、无写操作，也不影响收银或退款事务。它的两个原 SQL 已有确定的输出字段，FIN 可以继续承担页面卡片的二次聚合。

在 `money-app-api` 的 `contract.trade` 增加：

```text
FinanceRiskQuery
  listCashierRiskSummaries(LocalDateTime startInclusive, LocalDateTime endInclusive)
    -> [ { cashierName, orderCount, manualDiscountAmount, refundCount } ]
  listAbnormalOrders(LocalDateTime startInclusive, LocalDateTime endInclusive)
    -> [ { orderNo, createTimeLabel, cashierName, payAmount, costAmount,
           profitAmount, riskType } ]
```

两个快照均为普通不可变 DTO。`orderCount` 与 `refundCount` 可使用 `long`；金额使用 `BigDecimal`；时间展示值仍是 TRADE 原 SQL `DATE_FORMAT(create_time, '%m-%d %H:%i')` 生成的字符串，避免 FIN 重写时区或格式口径。实现放在 TRADE 报表/审计应用包，内部保留 `OmsOrderAuditMapper`，转换其现有查询结果后再返回快照。

### 必须保持的风险口径

- FIN 的空日期默认保持：开始为当天向前六天 `00:00:00`，结束为当天 `23:59:59.999999999`；显式日期也保持闭区间。契约不负责默认值或字符串解析。
- 收银员汇总仍在时间范围内按 `create_by` 分组，订单数是 `COUNT(id)`，手工优惠是 `SUM(IFNULL(manual_discount_amount, 0))`，退款单数仅计 `PARTIAL_REFUNDED` 与 `REFUNDED`。
- 异常单仍最多返回 50 条并按利润升序；原有三条风险谓词不变：支付额至少 10 且成本缺失/非正、最终销售额减成本为负且支付额为正、或手工优惠大于 50。
- 风控 SQL 当前没有金融有效状态过滤；迁移不得添加 `PAID` 等状态筛选。
- FIN 仍计算四张卡片：异常单数为异常快照数量，损失仅累计负利润绝对值，手工优惠和退款单数由收银员快照累加。接口仍返回现有 `Map<String, Object>` 字段及列表字段，直到独立的 HTTP DTO 收敛任务获授权。

### 验收与回滚

新增 TRADE/FIN 集成回归，以同一时间范围写入正常单、成本缺失单、倒挂亏损单、大额手工优惠单、部分退款单和全额退款单；断言两类快照、FIN 卡片与原页面字段一致，并验证日期边界和 50 条上限。迁移后运行专项 FIN 回归、全量 Maven 测试、打包、`architecture-scan.sh --check-new` 与 `git diff --check`。

若出现口径差异，回滚仅限 P2.4.1 本地提交：恢复 FIN 对审计 Mapper 的三处读取及删除的契约实现；不触及交接班、利润、经营分析或瀑布流。

## 后续顺序

1. P2.4.1 实现 TRADE `FinanceRiskQuery`，仅迁移风控两类审计读取。
2. P2.4.2 设计并迁移交接班支付、优惠和品牌贡献快照；先确认品牌名称 join 的 GMS 归属。
3. P2.4.3 分别迁移利润排行与活动复盘，保持它们的时间范围和状态集不同。
4. P2.4.4 按入口拆分经营分析/客流/审计查询，并将 SYS 策略读取与 TRADE 数据读取分开。
5. P2.4.5 最后处理瀑布流的 TRADE/GMS 组合公式，不创建跨所有者 Mapper。

## P2.4.2：交接班支付、优惠与品牌贡献快照

交接班的三个读取公式均属于 TRADE 订单/支付/明细数据，但品牌名称是 GMS 归属的显示档案。因此契约不返回 FIN 的 `BrandContributionVO`，也不允许 TRADE 查询 join `gms_brand`：TRADE 返回品牌 ID 和订单明细金额，FIN 使用既有 GMS `BrandNameQuery` 翻译名称，再装配原有品牌矩阵。

```text
FinanceShiftHandoverQuery
  listPaymentSummaries(startInclusive, endInclusive, cashierName)
    -> [ { methodCode, payTag, netAmount } ]
  getDiscountSummary(startInclusive, endInclusive, cashierName)
    -> { manualDiscount, voucherDiscount, memberCouponPay, waivedCouponAmount,
         voucherCount, refundAmount }
  listBrandContributions(startInclusive, endInclusive, cashierName)
    -> [ { brandId, revenue, couponConsumption } ]

BrandNameQuery.findNamesByIds(brandIds)
  -> { brandId: brandName }
```

FIN 保留班次起点字符串的解析、结束时间为调用时 `now`、空收银员到“全部收银员”的归一化、现金/余额/扫码分类、扫码标签聚合、净收入和应收现金计算。TRADE 保持以下互不合并的原 SQL 口径：支付按金融有效状态、闭区间和全额退款为零的 `net_amount` 回退公式；优惠/退款按订单闭区间和同一收银员过滤；品牌贡献按退货后的明细数量和单品券分摊聚合。品牌 ID 缺失或 GMS 无对应名称时保持原“无品牌/未知”回退。

P2.4.2 的回归以滚回事务写入一个现金支付订单、实际会员券/免券/满减券/手工优惠、品牌明细与 GMS 品牌档案，验证交接班金额、优惠字段和品牌名称/金额矩阵。路由、打印入口、响应字段和查询的事务边界不变。

## P2.4.3：利润排行与活动复盘快照

利润排行和活动复盘同属 TRADE 订单数据，但它们不能共用范围或状态过滤。为避免把 FIN DTO 暴露给 TRADE，新增独立查询契约：

```text
FinanceProfitQuery
  listProfitRankings(startInclusive)
    -> [ { goodsName, totalQuantity, totalSales, totalProfit } ]
  listCampaignReviews(startInclusive, endInclusive)
    -> [ { ruleName, usedCount, totalDiscount, totalRevenue } ]
```

利润排行保持近 30 天起点、无显式结束边界，以及 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED` 状态；数量、销售额和利润继续按退货后的订单明细数量计算。活动复盘保持近 3 个月起点至当前时刻闭区间，仅包含 `PAID`、`PARTIAL_REFUNDED`；满减券和会员券继续是两条独立 SQL 聚合。FIN 保留总优惠为零时 ROI 为零、否则按 `revenue / discount` 两位半舍五入，以及按 ROI 降序排序的展示规则。

回归以一个带唯一活动备注、满减券和会员券的已支付订单及其明细验证排行金额、活动使用次数、优惠/营收与 `6.67` ROI。不得把该回归的日期范围或状态集推广给其他 FIN 专项报表。

## P2.4.4：经营分析、客流与利润审计盘点

这一组入口虽然都由 `OmsSalesAnalysisServiceImpl` 提供，但不能视为同一张“经营报表”。当前读取面和所有者边界如下：

| FIN 入口/方法 | 当前读取 | 数据所有者 | 必须保留的口径 | 迁移边界 |
| --- | --- | --- | --- | --- |
| 绩效报表 `getPerformanceReport`、汇总卡片 `countOrderAndSales` | `OmsOrderAnalysisMapper.getPeriodAtomicStats` | TRADE | 输入范围由 FIN 解析；`PAID`、`PARTIAL_REFUNDED`；闭区间；按日/周/月的现有 SQL 分组；订单数、商品数、净销售额、成本额各自保持原聚合 | **P2.4.4.1 首个实施切片** |
| 销售看板 `getSalesDashboard` | 周期原子指标、商品排行、品牌分布、会员日统计 | TRADE；品牌名称当前由 SQL 读取 GMS；会员趋势展示在 FIN | 默认近 30 日、`DAILY` 指标和图表补零/装配规则不能随绩效契约改变 | 后续独立拆分，不能因复用周期指标而一次迁移整个看板 |
| 客流分析 `getTrafficAnalysis`、`getWeeklyTraffic`、`getMonthlyTraffic` | `OmsOrderTrafficMapper` 与 `SysStrategyMapper.getGlobalStrategy` | TRADE、SYS | TRADE 的小时/周/月分组、闭区间、`PAID`/`PARTIAL_REFUNDED`；SYS 的阈值、默认值、样本天数除数和 `STAY`/`OUT` 判定 | TRADE 客流数据与 SYS 策略读取分成两个契约，且三个入口保留各自默认窗口 |
| 品类销售、单品趋势 | 订单分析 Mapper，品类名称 SQL join GMS | TRADE、GMS | 销售与退货数量公式、时间范围、品类/商品显示名归属各自保持 | 先输出 TRADE 的 ID/数值快照，再由 GMS 翻译显示档案 |
| 利润审计 `getProfitAuditPage` | `OmsOrderAuditMapper.getProfitAuditPage` | TRADE | `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`；订单号和 `ANOMALY` 筛选；成本缺失/负毛利谓词；既有分页、排序和字段 | 独立分页契约；不能与无分页指标查询或风控异常清单合并 |

### P2.4.4.1：TRADE 周期经营原子指标

首个实现切片选择绩效报表和汇总卡片共同使用的周期原子指标。它完全由 TRADE 的订单表计算，没有 GMS 名称翻译、SYS 策略读取、分页或写入影响；同时它的消费者可以各自继续保持展示规则。

在 `money-app-api` 的 `contract.trade` 增加 Java 8 兼容的窄契约：

```text
FinanceOperatingAnalysisQuery
  listPeriodMetrics(startInclusive, endInclusive, periodDimension)
    -> [ { period, orderCount, goodsCount, netSalesAmount, costAmount } ]
```

快照是普通不可变 DTO：`period` 为既有 SQL 输出的字符串，数量使用 `long`，金额使用 `BigDecimal`。`periodDimension` 仅接受既有 `DAILY`、`WEEKLY`、`MONTHLY` 分支；契约实现位于 TRADE，保留 `OmsOrderAnalysisMapper` 或其等价的 TRADE 内部 SQL。不得向 FIN 暴露 `AnalysisAtomicDataDTO`、订单 Entity、Mapper、QueryWrapper 或动态 SQL 细节。

FIN 继续负责：日期/默认范围解析，绩效响应 DTO 映射及倒序展示，和汇总卡片的销售额、成本、利润、客单价等二次计算。首轮仅迁移 `getPerformanceReport` 与 `countOrderAndSales`；`getSalesDashboard` 即使复用同一 TRADE 原子指标，也留待其商品、品牌、会员图表一起设计，避免一个入口同时落入新旧读取路径。

P2.4.4.1 已按此边界实施：`FinanceOperatingAnalysisQuery` 和不可变 `FinanceOperatingMetricSnapshot` 由 TRADE 实现，内部仍使用既有周期 Mapper；FIN 不再在绩效报表和汇总卡片入口直接读取该 Mapper。回归覆盖 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`、闭区间边界及日/周/月分组，并断言 FIN 的客单价、成本和利润展示结果。

### 客流、利润审计与后续切片约束

- 客流的 TRADE 契约只返回已聚合的小时/周/月访问量与销售额；FIN 不将 `dayOfWeek` 的 MySQL 映射、空时段补零、阈值默认值或 `STAY`/`OUT` 判定下沉到 TRADE。SYS 另行提供只读策略值契约，且仍由 FIN 按入口保留 28、90、180 日默认窗口及 `4`、`days / 7.0`、`days / 30.43` 的样本除数。
- 利润审计使用独立的 TRADE 分页快照和筛选对象，保留订单号、状态与 `ANOMALY` 语义，及按创建时间倒序的分页行为。P2.4.1 的异常清单最多 50 条且按利润升序，不能与它合并。
- 销售看板、品类销售和单品趋势分别在后续任务中逐入口迁移。涉及品牌/品类名称时，TRADE 只提供标识与数值，GMS 通过已有或新增的窄名称查询承担显示档案；不得继续让 FIN 或 TRADE 跨域 join GMS。

### 验收与回滚

P2.4.4.1 的回归在同一滚回事务内写入已支付、部分退款和非金融有效订单，并覆盖日/周/月分组及起止闭区间。断言 TRADE 快照与 FIN 绩效、汇总卡片的订单数、商品数、净销售额、成本、利润和客单价一致。迁移后运行专项 FIN 回归、全量 Maven 测试、打包、架构门禁和空白检查。

若发现差异，回滚只恢复 FIN 对 `getPeriodAtomicStats` 的两处读取并删除 P2.4.4.1 契约实现；不触及销售看板、客流、品类/单品趋势、利润审计或瀑布流。

## P2.4.4.2：销售看板商品、品牌与会员趋势盘点

`GET /oms/analysis/dashboard` 是一个固定的四段式 FIN 装配入口，不能以“经营分析”名义同客流、品类或单品趋势合并。FIN 解析开始/结束时间：空值为当天向前 29 日 `00:00:00` 到当天 `23:59:59.999999999`，显式日期同样使用闭区间；它还负责按日期补零、`MM-dd` 标签、客单价两位半舍五入和响应字段组装。

| 看板段 | 当前读取与数据归属 | 必须保持的口径 | 设计边界 |
| --- | --- | --- | --- |
| 基础趋势与卡片 | `getPeriodAtomicStats(..., DAILY)`；TRADE | `PAID`、`PARTIAL_REFUNDED`；闭区间；净销售额、成本、订单数及订单明细净件数；仅净销售额大于零的日订单数计入卡片总订单数 | 复用 P2.4.4.1 `FinanceOperatingAnalysisQuery` 的 `DAILY` 快照；FIN 保留补零和卡片规则 |
| 商品排行 | `getTopGoodsRank`；TRADE 订单明细 | `quantity - return_quantity` 的净销量与乘以订单明细 `goods_price` 的销售额；仅正净销量；按销量降序，最多 50 条 | 新增 TRADE 商品排行快照，保留订单明细中的历史 `goodsName`，不为名称回查 GMS |
| 品牌销售分布 | 订单明细聚合加 `gms_brand` 名称 join；数值属 TRADE，名称属 GMS | 与商品排行相同的金融状态、闭区间和净数量/金额；仅正销售额，按金额降序；空或缺失档案展示 `无品牌/未知` | TRADE 仅返回 `brandId` 与销售额；FIN 使用既有 GMS `BrandNameQuery` 批量翻译再装配 `BrandSalesVO` |
| 会员/散客趋势 | `getDailyMemberStats`；TRADE 订单历史字段 | 日分组；`member_id > 0 OR vip = 1` 为会员；金融状态与闭区间同上；销售额、订单数按日/身份分别聚合 | 新增 TRADE 会员日趋势快照；不读取 UMS 当前会员档案，FIN 保留每日补零及两条 ASP 曲线 |

### 拟定所有者查询契约与实施顺序

基础趋势不创建重复契约，直接消费既有 `FinanceOperatingAnalysisQuery.listPeriodMetrics(startInclusive, endInclusive, "DAILY")`。商品、品牌数值和会员趋势则新增独立的 Java 8 不可变快照，避免把 FIN 的 `SalesDashboardVO` 或现有可变 Mapper DTO 暴露给 TRADE：

```text
FinanceSalesDashboardQuery
  listTopGoods(startInclusive, endInclusive)
    -> [ { goodsId, goodsName, salesQuantity, salesAmount } ]
  listBrandSales(startInclusive, endInclusive)
    -> [ { brandId, salesAmount } ]
  listDailyMemberMetrics(startInclusive, endInclusive)
    -> [ { date, member, orderCount, salesAmount } ]
```

`goodsName` 是订单明细保存的交易时名称，属于 TRADE 事实快照；`brandId` 可为空，GMS 不存在时 FIN 仍使用原有回退显示。`date` 保持原 SQL 的 `yyyy-MM-dd` 键，不让 TRADE 承担 FIN 的展示格式。数量使用 `long`，金额使用 `BigDecimal`，标识使用 `Long`，会员标记使用 `boolean`；所有对象均为普通 `final` 字段、构造函数和 getter 的 Java 8 类。

P2.4.4.2.1 的实施将一次迁移该单一 `/dashboard` 入口的四段读取：基础 `DAILY` 复用已有契约，其余三段使用新 TRADE 快照，品牌名经 `BrandNameQuery` 翻译。FIN 的 `FinanceMetricAssembler` 继续掌握日期补零、订单数卡片判定、ASP 及成员/散客曲线；不改 HTTP 路由、响应字段、表、Flyway 或事务边界。

P2.4.4.2.1 已按此边界实施。TRADE `FinanceSalesDashboardQuery` 提供商品排行、品牌 ID 营收和会员日趋势三个不可变快照；品牌聚合 SQL 已删除 `gms_brand` join。FIN 复用 `FinanceOperatingAnalysisQuery` 的日快照，且只在 FIN 侧通过 `BrandNameQuery` 批量翻译名称并保留回退值。

### 验收与回滚

回归在同一滚回事务写入两个日期的已支付、部分退款、全额退款订单及明细：覆盖一个退货后仍为正销量商品、一个净销量为零商品、已知品牌、空品牌、会员和散客。断言 TRADE 快照中的过滤/排序/限额，GMS 名称和 `无品牌/未知` 回退，以及 FIN 的连续日期、卡片、商品排行、品牌分布与双线 ASP 字段。若存在口径差异，仅回滚 P2.4.4.2.1 的 dashboard 消费端和新增契约；不影响 P2.4.4.1 绩效/汇总、客流或其他专项报表。
