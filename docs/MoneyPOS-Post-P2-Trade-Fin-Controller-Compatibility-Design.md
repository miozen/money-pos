# MoneyPOS：消除 TRADE → FIN `OmsOrderController` 兼容调用设计

## 目标与边界

本独立架构债务任务只消除 `TRADE` 的 `OmsOrderController` 对 FIN 实现类
`OmsSalesAnalysisService` 的既有依赖。它来自 OMS 历史路由将两个订单读接口暂时
委托给 FIN 报表服务的兼容安排，是 P2 最终复核中唯一的跨 Feature 实现 import。

**实施状态：** 已由 P2.6 实施。`OmsOrderController` 已改为注入两个独立的 TRADE
应用适配服务；最终架构扫描的跨 Feature 实现 import 为 0。以下内容保留为该实施的
边界和兼容依据。

本任务的实施必须保持以下外部兼容面不变：

| 路由 | 权限 | 入参/响应 | 当前调用方 |
| --- | --- | --- | --- |
| `GET /oms-order/statistics` | `oms:order:list` | `startTime`、`endTime`（`yyyy-MM-dd HH:mm:ss`），`OrderCountVO` | OMS API 与 POS `SalesOrderModal` |
| `GET /oms-order/profit-audit` | `oms:order:audit` | `OmsOrderQueryDTO`，`PageVO<ProfitAuditVO>` | OMS API |

不在本任务范围：改变 HTTP 路由、权限、页面字段、数据库表、Flyway、事务边界，或移动
`OmsSalesAnalysisController` 的 FIN 专项报表能力。也不借此合并统计看板与利润审计的
查询口径。

## 当前盘点

`OmsOrderController` 位于 TRADE，却注入 FIN 的 `OmsSalesAnalysisService`，仅调用下列
两个方法：

| OMS 入口 | 现有 FIN 方法 | 已存在的数据所有者输入 | 口径要点 |
| --- | --- | --- | --- |
| `/statistics` | `countOrderAndSales(startTime, endTime)` | TRADE `FinanceOperatingAnalysisQuery.listPeriodMetrics(..., "DAILY")` | 直接采用控制器绑定的起止时间；按日原子指标累加订单数、净销售额与成本，再计算利润。不可补默认日期。 |
| `/profit-audit` | `getProfitAuditPage(queryDTO)` | TRADE `FinanceProfitAuditQuery.getProfitAuditPage(page, size, orderNo, status)` | 保留订单号、`ANOMALY`、状态集、分页元数据和 `create_time DESC` SQL 排序；FIN 现仅将快照映射回旧响应 DTO。 |

两个 FIN 方法仍由 `OmsSalesAnalysisController` 的 FIN 报表接口使用（利润审计为
`/oms/analysis/audit-page`）；因此不能为消除兼容 import 而删除 FIN 方法或改变其路由。

目前不存在 TRADE 直接读取 FIN Mapper、Entity 或数据库表的情况；问题仅是
`OmsOrderController` 的 Java 实现 import/注入方向错误。

## 所有者设计

实施时在 `feature.trade.application.orderquery` 新增两个窄的 TRADE 应用服务，而不是
让 REST Controller 直接拼装快照，或把两个互不相同的报表公式放进一个泛化服务。

| TRADE 适配服务（建议名称） | 输入 | 输出 | 内部依赖 | 保留的责任 |
| --- | --- | --- | --- | --- |
| `OmsOrderStatisticsQueryService` | `LocalDateTime startTime, endTime` | `OrderCountVO` | `FinanceOperatingAnalysisQuery` | 请求指定的 `DAILY` 原子指标，并按现有顺序累加 `orderCount`、`netSalesAmount`、`costAmount`；填充 `orderCount`、`totalSales`、`saleCount`、`costCount`、`profit`。 |
| `OmsOrderProfitAuditQueryService` | `OmsOrderQueryDTO` | `PageVO<ProfitAuditVO>` | `FinanceProfitAuditQuery` | 原样转发 `page`、`size`、`orderNo`、`status`，将不可变页/行快照映射为既有 API DTO。 |

这里的两个 `Finance*Query` 名称是已建立在 `money-app-api` 的 TRADE 所有者查询契约；
它们在 TRADE 内被消费不会产生 TRADE → FIN 实现依赖。返回的 `OrderCountVO`、
`ProfitAuditVO`、`OmsOrderQueryDTO` 和 `PageVO` 都是既有 API/Web 模型，非 FIN
Entity 或 FIN 实现类型。

实施后调用链应为：

```text
GET /oms-order/statistics
  -> TRADE OmsOrderStatisticsQueryService
  -> TRADE FinanceOperatingAnalysisQuery
  -> TRADE OmsOrderAnalysisMapper

GET /oms-order/profit-audit
  -> TRADE OmsOrderProfitAuditQueryService
  -> TRADE FinanceProfitAuditQuery
  -> TRADE OmsOrderAuditMapper
```

FIN 的 `OmsSalesAnalysisService` 继续通过相同的 API 契约为 `/oms/analysis/*` 组装
FIN 专项报表；两个 Feature 不再相互调用实现类。

## 迁移步骤与验收

1. 先为两个 TRADE 适配服务增加各自的应用层回归：统计覆盖给定闭区间、`PAID`/
   `PARTIAL_REFUNDED`/`REFUNDED` 的既有周期指标结果，以及销售、成本和利润字段；审计覆盖
   订单号、`ANOMALY`、状态、分页和排序，不重写现有 SQL。
2. 给 `OmsOrderController` 注入这两个 TRADE 服务，并仅替换两个方法委托；删除
   `OmsSalesAnalysisService` import 和字段。`@RequestMapping`、两个 `@GetMapping`、
   `@PreAuthorize` 和参数注解逐字保留。
3. 对两个 HTTP 端点补/扩展控制器兼容回归，断言原有参数绑定、权限名与 JSON 字段仍可用；
   FIN 的 `/oms/analysis/audit-page` 回归继续验证其独立存在。
4. 在隔离 `money_pos_test` 上执行 FIN/OMS 专项与全量 Maven 测试、`mvn -q package -DskipTests`、
   `scripts/architecture-scan.sh --check-new` 和 `git diff --check`。目标是扫描中的
   `Cross-Feature Implementation Import` 从 1 降为 0，且不新增 Controller → Mapper import。

## 拒绝的替代方案

- **控制器直接注入两个 `Finance*Query` 契约：** 虽可消除实现 import，但会把快照映射和
  金额汇总放进 REST 层，并向 TRADE Controller 暴露误导性的 FIN 命名。
- **把两个入口继续留在 `OmsSalesAnalysisService`：** FIN 的报表服务会继续成为 TRADE
  OMS 路由的运行时依赖，架构扫描基线债务无法消除。
- **创建一个“订单报表万能服务”：** 统计汇总与利润审计的状态、分页和计算规则不同，会使
  后续口径变更重新扩大牵扯面。

## 风险与后续边界

这项迁移会在 TRADE 与 FIN 两处暂时存在同一所有者快照到旧 DTO 的映射；这是为保持两个
历史 HTTP 面独立兼容而接受的小范围适配重复，不应通过跨 Feature 复用实现来“去重”。若将来
需要统一 API DTO，应另立 API 演进任务并同步前端，不与本债务清理合并。

本设计不处理 P2 已单列的 73 个共享 Entity import。该问题属于 Entity 物理归属收敛，
需要按 `MoneyPOS-Entity-Ownership-and-DTO-Plan.md` 的独立优先级逐个处理。
