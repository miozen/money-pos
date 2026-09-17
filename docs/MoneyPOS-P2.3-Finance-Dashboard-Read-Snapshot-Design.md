# MoneyPOS P2.3：FIN 财务大盘读快照设计

## 目的与边界

`FinanceDashboardServiceImpl` 是财务页面的组装者，不是订单、库存或会员资产表的所有者。本设计将其当前的跨域 `Mapper`、Entity 与 `UmsMemberService` 读取拆为 TRADE、GMS、UMS 三个只读快照契约；FIN 保留日期解析、图表组装、金额展示和 HTTP 路由。

本轮不改变 `/finance/dashboard`、`/finance/channel-mix`、`/finance/dashboard/asset` 路由、权限或返回字段；不改订单状态集、MyBatis 租户行为、金额公式和闭区间日边界；不迁移财务瀑布流、交接班、利润或风控报表，它们属于 P2.4。

## 现有兼容口径

| FIN 入口 | 数据所有者 | 当前读取与必须保持的行为 |
| --- | --- | --- |
| `getDashboardData(date)` | TRADE | 当日订单只取 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`；按 `create_time` 从当日 `00:00:00` 到 `23:59:59.999999999` 闭区间。核心指标需要订单金额、四类优惠、实收、最终销售额和成本；支付明细按日期、支付方式、标签分组，已退款订单支付净额为零。七日退款趋势仍以 `SUM(pay_amount)-SUM(final_sales_amount)` 计算。 |
| `getChannelMixAnalysis(startDate,endDate)` | TRADE | 默认近七日；支付明细沿用同一支付净额 SQL；优惠趋势按日期汇总 `actual_coupon_deduct` 与 `use_voucher_amount`，状态仍固定为 `PAID`、`PARTIAL_REFUNDED`、`REFUNDED`。 |
| `getDashboardData(date)` | GMS | 当日库存单据仅取 `OUTBOUND`、`CHECK`，只需单据类型和总金额；FIN 只将负金额绝对值计入毛利扣减，零/正金额不影响该值。 |
| `getDashboardData(date)` | UMS | 当日会员流水只取 `RECHARGE`、`REVERSAL`，以 `real_amount` 合计；七日趋势按日期汇总同一金额；总负债为所有未删除租户范围内 `balance > 0` 的余额之和。 |
| `getAssetDashboard()` | TRADE、UMS | 今日订单资产概览取当天金融有效订单的 `final_sales_amount`、`waived_coupon_amount`、`actual_coupon_deduct`；会员资产构成取未删除会员的 `balance`（本金）与 `coupon`（赠金），FIN 仅计算两者比例并保留零值回退。 |

`FinanceDashboardAssembler` 继续在 FIN 内保留：支付方式/标签分组、现金/扫码/余额/充值饼图分类、趋势日期补零、退款非负钳制、库存损耗扣减和资产比例计算。契约只返回其计算所需的原子快照，不能返回 Mapper 的 `Map<String,Object>` 或跨域 Entity。

## 契约与实施切片

### P2.3.1：GMS 库存单据快照（首个安全切片）

在 `money-app-api` 的 `contract.goods` 增加 `FinanceInventoryDocumentQuery` 及不可变 `FinanceInventoryDocumentSnapshot`：

```text
listDailyFinancialDocuments(LocalDate date)
  -> [ { documentType, totalAmount } ]
```

GMS 实现在本 Feature 内保留 `GmsInventoryDocMapper` 和现有 `OUTBOUND`/`CHECK`、闭区间查询。FIN 将快照映射给现有装配器，或将装配器参数收窄为该快照；不得把 `GmsInventoryDoc` 留在 FIN。该切片没有写操作、没有跨所有者 join，且只影响毛利中的库存损耗，因此先实施。

**已完成。** `FinanceInventoryDocumentQuery` 与不可变 `FinanceInventoryDocumentSnapshot` 已由 GMS 实现；FIN 的服务和装配器均不再导入库存单据 Mapper 或 Entity。集成回归以迁移前后的毛利差值验证：`OUTBOUND -5.50` 只使毛利减少 `5.50`，`CHECK +3.25` 与 `INBOUND +99.00` 不增加库存损耗。

### P2.3.2：UMS 会员资产快照

在 `contract.member` 增加 `FinanceMemberAssetQuery`，以两个不可变数据结构表达不同时间粒度：

```text
listDailyRechargeEntries(LocalDate date)
listDailyRechargeTotals(LocalDate startInclusive, LocalDate endInclusive)
getPositiveBalanceTotal()
getAssetComposition()
```

单笔充值快照只含 `realAmount`；按日快照只含 `date`、`totalAmount`；资产构成只含 `principalAmount`、`giftAmount`。UMS 在自身边界内保留 `UmsMemberLogMapper`、`UmsMemberMapper` 与逻辑删除/租户处理。FIN 不再使用 `UmsMemberService.listObjs(UmsMember)`，这一步同时消除当前 FIN→UMS 实现 import。

### P2.3.3：TRADE 订单/支付快照

在 `contract.trade` 增加 `FinanceOrderPaymentQuery` 和下列不可变快照，按用途分开，避免隐含改变状态或金额口径：

```text
getDailyOrderMetrics(LocalDate date)
listDailyPaymentSummaries(LocalDate startInclusive, LocalDate endInclusive)
listDailyRefundBases(LocalDate startInclusive, LocalDate endInclusive)
listDailyChannelDiscounts(LocalDate startInclusive, LocalDate endInclusive)
getTodayAssetOrderMetrics(LocalDate date)
```

`DailyOrderMetrics` 逐订单保留 FIN 核心指标所需的金额字段，或在 TRADE 内以逐日聚合快照等价计算；实施时优先选择能逐字段与现有 `assembleCoreMetrics` 对照的表达。支付快照保留 `date`、`methodCode`、`payTag`、`netAmount`；退款基准保留日期、支付额和最终销售额；渠道优惠保留日期、实际会员券抵扣和满减券抵扣；今日资产订单快照保留三项资产概览金额。

TRADE 内部继续使用 `OmsOrderMapper`、`OmsOrderPayMapper`，并逐项复用原 SQL：支付净额对 `REFUNDED` 为零、订单金融有效状态为 `PAID`/`PARTIAL_REFUNDED`/`REFUNDED`、所有日期范围仍为闭区间。FIN 不得接收 `OmsOrder`、`OmsOrderPay` 或通用 `Map`。

### P2.3.4：FIN 组装收敛与验收

三个所有者契约到位后，删除 `FinanceDashboardServiceImpl` 对 `OmsOrderMapper`、`OmsOrderPayMapper`、`GmsInventoryDocMapper`、`UmsMemberLogMapper`、`UmsMemberService` 和 `FinanceReportMapper` 的依赖。`FinanceReportMapper` 的两项资产概览 SQL 分别归入 P2.3.2 UMS 与 P2.3.3 TRADE；其跨 TRADE/GMS 的瀑布流 SQL 保留至 P2.4。

扩展 `FinanceFeatureIntegrationTest`，以受控订单、支付、库存单据、会员充值和余额资产校验：核心金额、负库存损耗、支付标签、七日退款/充值趋势、渠道优惠、资产本金/赠金比例与零值回退。最后运行全量测试、打包和 additions-only 架构门禁。

## 迁移后的依赖形态

```text
FinanceController
        |
FinanceDashboardServiceImpl + FinanceDashboardAssembler
        |                 |                  |
 FinanceOrderPayment  FinanceInventory   FinanceMemberAsset
      Query (TRADE)    DocumentQuery(GMS)    Query (UMS)
```

FIN 仅依赖 API 契约；每个查询实现只在数据所有者内部触达 Entity、Mapper 和 SQL。所有调用仍是同一进程、同一数据库的同步只读调用，不引入 Maven 模块拆分或分布式通信。

## 验收风险

- `LocalTime.MAX` 与 SQL 的 `<=` 是既有日边界；快照接口不能改为右开区间而未逐项证明等价。
- 支付净额、订单实收/最终销售额和退款趋势看似相近，实际公式不同，必须维持独立快照。
- `RECHARGE` 与 `REVERSAL` 是否为正/负金额由原始流水决定；契约不得自行过滤或取绝对值。
- 资产构成查询目前忽略租户拦截；实施前须在 UMS 内显式复核该行为是否为既有业务口径，不能在迁移中无意改变。

## 下一最小任务

**P2.3.2：新增 UMS `FinanceMemberAssetQuery`，迁移当日充值、七日充值趋势、正余额总额和资产本金/赠金构成读取。**
