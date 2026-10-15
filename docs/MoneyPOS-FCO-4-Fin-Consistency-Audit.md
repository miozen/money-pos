# MoneyPOS FCO-4：FIN 一致性审查记录

> 状态：**只读审查完成，未实施优化**；审查日期：2026-10-15；适用分支：`dev`。

## 现状地图

FIN 是纯读侧组合 Feature，不持有订单、会员、库存或权益的写入权。`FinanceDashboardServiceImpl` 组合 TRADE
订单/支付/非商品收款、UMS 充值资产、GMS 库存单据快照；`FinanceReportServiceImpl` 组合 TRADE 订单/非商品收款与
GMS 采购瀑布快照；利润、风险、交接班和经营分析均经 TRADE/GMS/SYS 的 Entity-free 查询契约。`FinanceFeatureIntegrationTest`
覆盖退款、非商品收款、库存单据、瀑布和风险/报表口径。

`FinanceController` 的仪表盘、渠道、利润、交接班、风控、资产驾驶舱及交接班打印均使用既有 `omsOrder:list`。
打印是已受保护的后置硬件副作用，不在本审查调整范围。

## 已确认约束

1. FIN 不得写订单、库存、会员资产或权益表；金额、退款和非商品收款口径由上游快照契约定义。
2. 不在 FIN 重算结算/退款公式，也不改变 TRADE/GMS/UMS 的事务所有权。
3. 报表与仪表盘必须继续输出既有 DTO/响应字段，且无迁移或历史重算。

## 已确认问题

| 编号 | 影响 | 证据 | 风险 |
| --- | --- | --- | --- |
| FIN-1 | 中 | `FinanceReportController.GET /finance/report/waterfall/daily` 输出财务全口径日结，却没有 `@PreAuthorize`；相邻 FIN 路由均使用 `omsOrder:list`。 | 已认证但无订单/财务读取权限的用户可读取收入、退款、采购与利润瀑布数据。 |

## 最小优化候选

**FIN-OPT-1：瀑布日报权限对齐。** 为该 GET 路由复用 `omsOrder:list` 并增加路由权限契约测试；不改报表数据、公式、
路由、角色授权、迁移或上游查询。
