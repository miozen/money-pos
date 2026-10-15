# MoneyPOS HOME 趋势图粒度调整实施计划

> 状态：**已完成，待浏览器验收**；适用分支：`dev`；确认日期：2026-10-15。

## 目标与边界

只调整后台 HOME 的“营业额与利润趋势”读模型展示粒度，不改变订单、非商品收款、库存、数据库结构、事务、接口路径或响应字段。

| 首页范围 | 冻结展示粒度 |
| --- | --- |
| 今日（近 7 日） | 按日 |
| 本月 | 按日 |
| 本年 | 按月（当年 1 月至当前月，缺失月补零） |
| 总计/库存 | 营业额与利润按年；库存仍是当前库存成本货值，不构造库存历史趋势 |

## 实施与验证

1. HOME 先合并 TRADE 的按日订单趋势和非商品收款，再仅在 HOME 读侧按月或按年二次汇总。
2. 本年按月返回 `MM月` 标签；总计按年返回 `yyyy` 标签。日粒度保持既有 `MM-dd` 标签与零值补齐规则。
3. 扩展 HOME 集成测试，覆盖本年的月度累计与空月，以及总计的年度累计。
4. 按隔离 `money_pos_test` 协议运行聚焦回归、打包、架构门禁和空白检查；更新接力文档后提交、推送并确认同步。

## 实施事实

- `HomeServiceImpl` 保持 TRADE 的订单日趋势和非商品收款日趋势先合并，再在 HOME 内二次分桶；因此销售额和利润仍使用同一批原始金额，未改变订单数、成本、库存或品牌饼图口径。
- 本年固定返回当年一月至当前月的连续月点，零营业额月份为零；总计仅对已有经营数据按自然年汇总，不伪造尚无经营数据年份。
- 隔离 `money_pos_test` 的 `HomeCountSnapshotCharacterizationTest` 通过（11 tests，0 failures/errors）；`mvn -q package -DskipTests`、`bash scripts/test-architecture-scan.sh`、`bash scripts/architecture-scan.sh --check-new` 与 `git diff --check` 通过。
