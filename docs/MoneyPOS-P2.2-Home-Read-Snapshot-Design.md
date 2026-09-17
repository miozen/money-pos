# MoneyPOS P2.2：HOME 订单与会员图表只读快照设计

## 结论

HOME 目前有四类跨域读取：首页区间订单汇总、HOME 日快照订单输入、订单趋势/品牌营收图表，以及会员等级分布图表。它们看似都在回答“销售情况”，但现有状态过滤、时间边界和金额公式并不一致，不能合并成一个泛化的报表接口。

P2.2 因此按数据写入所有者提供小型只读快照：TRADE 负责订单与订单明细口径，UMS 负责有效会员的品牌等级人数；HOME 只保留时间范围转换、页面 DTO 组装、同比环比计算，以及自有 `OmsDailySummary` 的补偿和写入。

本设计不调整 HTTP 路由、数据库、Flyway、订单状态、日快照写入时机或现有数值口径。

## 当前行为基线

| HOME 场景 | 当前数据源与口径 | 必须保留的兼容面 | 将来的数据所有者 |
| --- | --- | --- | --- |
| `HomeService.homeCount()`（服务级遗留入口） | `OmsOrderMapper.selectMaps`；状态为 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`；销售额为 `SUM(IFNULL(final_sales_amount, pay_amount))`，成本为 `SUM(cost_amount)`，利润由 HOME 计算为销售减成本 | `today`、`month`、`year` 使用 `[start, nextStart)`；`total` 无时间条件；空值归零 | TRADE |
| `GET /home/count` 的当日日快照 | `OmsOrderAnalysisMapper.getPeriodAtomicStats`；状态仅 `PAID`、`PARTIAL_REFUNDED`；销售额为 `SUM(IFNULL(final_sales_amount, 0))`；时间为 `[当天 00:00:00, 23:59:59.999999999]` | `OmsDailySummary` 的销售、成本、利润、订单数、ASP；读取时补齐近 7 天并重算当天 | TRADE；HOME 继续写自己的日快照 |
| `GET /home/count` 的月/年/总计对比 | HOME 内嵌 JDBC 查询 `oms_order`；状态为 `PAID`、`COMPLETED`、`PARTIAL_REFUNDED`；销售额回退至 `pay_amount`；利润在 SQL 中计算；时间为 `[start, nextStart)` | 月/年同比字段和 ASP 的舍入规则 | TRADE |
| `GET /home/charts` 营业趋势 | `OmsOrderDetailMapper.getTrendData` 实际查询 `oms_order`；状态为 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`；按订单 `create_time` 按日分组，销售额/利润均来自订单总额 | `today` 特例展示最近 7 天至当前时刻；其他范围为 `[start, end)`；输出 `date`、`sales`、`profit` | TRADE |
| `GET /home/charts` 品牌营收占比 | `OmsOrderDetailMapper.getBrandPieData`；订单明细状态为 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`，按订单 `create_time` 过滤；净数量乘明细单价；品牌空值为“无品牌/未知” | 同一时间范围、退货数量扣减、仅返回金额大于零的品牌、`name`/`value` 字段 | TRADE |
| `GET /home/charts` 会员等级柱图 | `UmsMemberBrandLevelMapper` 联结会员和品牌；仅 `ums_member.deleted=0`；无时间过滤，是当前有效会员状态 | `brandName`、`levelCode`、`count`；不随图表时间筛选变化 | UMS（品牌名通过既有 GMS 窄查询取得） |

`/home/count` 实际由 `DecisionEngineServiceImpl` 提供；`HomeService.homeCount()` 不是当前控制器路径，但仍须在迁移中保持兼容，不能因未被页面调用而删除。

## 契约边界

### TRADE：`HomeOrderReadQuery`

在 `money-app-api` 增加 HOME 专用只读查询接口和不可变快照，不暴露 `OmsOrder`、`QueryWrapper`、Mapper 或 `Map<String, Object>`。接口方法按现有口径命名，避免调用方误用相近但不同的统计：

| 方法 | 快照内容 | 对应现有实现 |
| --- | --- | --- |
| `summarizeHomeCount(startInclusive, endExclusive)` | `orderCount`、`saleCount`、`costCount`、`profit` | `HomeServiceImpl.executeAggregateQuery()` |
| `summarizeDailySnapshot(date)` | `salesAmount`、`costAmount`、`orderCount` | `getPeriodAtomicStats(..., "DAILY")` 的单日结果；HOME 继续计算利润和 ASP 并写入快照 |
| `summarizeDashboardRange(startInclusive, endExclusive)` | `orderCount`、`saleCount`、`profit` | `DecisionEngineServiceImpl.queryActualData()`；HOME 继续计算 ASP 和趋势率 |
| `listSalesTrend(startInclusive, endExclusive)` | `date`、`sales`、`profit` | `OmsOrderDetailMapper.getTrendData()` |
| `listBrandSales(startInclusive, endExclusive)` | `name`、`value` | `OmsOrderDetailMapper.getBrandPieData()` |

TRADE 的实现可以继续在本域使用既有 `OmsOrderMapper`、`OmsOrderAnalysisMapper` 和 `OmsOrderDetailMapper`。每个方法应把状态集和边界固定在实现内，而不接收“状态列表”这类会泄露统计策略的参数。

### UMS：`HomeMemberDistributionQuery`

在 `money-app-api` 增加一个只读接口，返回 `HomeMemberBrandLevelSnapshot(brandName, levelCode, count)` 列表。UMS 负责：

1. 统计未逻辑删除会员的品牌等级人数；
2. 通过已有 GMS `BrandNameQuery` 将已知品牌 ID 转为名称；
3. 对未解析名称保留现有回退值（原始品牌值）；
4. 不接收 HOME 的时间范围，因为本图表本来就是即时会员资产状态。

这让 HOME 不再调用 UMS Mapper，也消除 UMS Mapper 直接联结 `gms_brand` 的长期边界问题；UMS 到 GMS 的品牌名称翻译仍是已验证的窄查询契约。

## 实施切片与顺序

- [x] **P2.2 设计**：固定六个现有读口径，确认不复用 FIN 的分析服务，详见本文档。
- [x] **P2.2.1 首页区间订单汇总**：已实现 `summarizeHomeCount`，并迁移 `HomeService.homeCount()` 的四次订单聚合；特征测试覆盖 `PAID`/`REFUNDED` 计入、`CLOSED` 排除、零值、金额成本利润和 `[start, end)` 边界。
- [x] **P2.2.2 日快照与大盘区间汇总**：已实现 `summarizeDailySnapshot` 与 `summarizeDashboardRange`，并迁移 `DecisionEngineServiceImpl` 的 TRADE Mapper/JDBC 查询；HOME 写入、补偿和警报时机保持不变。特征测试覆盖日快照与综合大盘不同的状态口径、落库的销售/利润/订单数，以及月大盘输出。
- [x] **P2.2.3 销售趋势与品牌营收图表**：已实现 `listSalesTrend`、`listBrandSales`，并迁移 HOME 的订单明细 Mapper 依赖；特征测试覆盖 `today` 最近 7 天特例、`month/year/total` 时间范围、订单趋势金额与退货数量扣减后的品牌营收。
- [x] **P2.2.4 会员等级图表**：已实现 `HomeMemberDistributionQuery`，迁移 HOME 的 UMS Mapper 依赖，并用 `BrandNameQuery` 保持品牌名称；集成测试覆盖有效/逻辑删除会员、品牌名转换，以及不随 `today/month` 变化的输出。
- [ ] **P2.2.5 验收**：执行 HOME 集成回归、全量 `mvn test`、`mvn package -DskipTests` 与架构门禁；人工确认 `/home/count` 和 `/home/charts` 的字段与页面可用。

## 已识别风险

- 三种订单汇总状态集不同：服务级汇总和趋势包含 `REFUNDED`，日快照不包含，综合大盘含历史 `COMPLETED`。这是当前行为，不应在 P2.2 顺手统一。
- 日快照使用闭区间到 `LocalTime.MAX`，其他区间使用右开区间；替换时混用会导致临界时刻重复或丢失。
- 品牌饼图用订单明细状态过滤，而不是父订单状态；实现必须先特征化该现有 SQL，再考虑产品规则修正。
- HOME 日快照读取会补偿缺失日期并覆写当天数据；P2.2 只替换输入读取，不能改触发与写入顺序。
- 会员等级图表没有时间维度。前端传入的 `today/month/year/total` 仅影响订单图表，不影响该图表。

## 下一最小任务

**P2.2.5：完成 HOME 报表快照验收，复核 `/home/count`、`/home/charts` 输出、全量自动化验证和架构门禁。**
