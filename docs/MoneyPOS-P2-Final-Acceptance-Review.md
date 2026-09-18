# MoneyPOS P2：FIN/HOME 报表读模型最终验收与复核

## 结论

P2 定义的 FIN/HOME 报表读模型收敛范围已完成。FIN 与 HOME 的跨所有者报表输入均通过 `money-app-api` 场景查询契约取得；调用方不再直接读取 TRADE、GMS、UMS 的 Entity、Mapper 或 Feature 实现。

P2 不包含 Entity 物理搬迁、Maven 子模块拆分、HOME 自有日快照写入时机调整，或既有全局架构基线债务的清零。这些项目不能被本结论隐含为已完成。

## FIN/HOME 调用面复核

| 调用方 | 复核结果 | 保留责任 |
| --- | --- | --- |
| FIN 财务大盘 | 订单/支付由 TRADE、库存单据由 GMS、会员资产由 UMS 快照提供 | 日期解析、图表/金额展示组装 |
| FIN 专项报表与经营分析 | 风控、交接班、利润、周期指标、看板、客流、品类/单品、利润审计及瀑布流均消费所有者快照 | ROI、分页 DTO、日期补零、排序和响应装配 |
| HOME 首页与图表 | 库存估值、订单汇总/趋势/品牌、会员分布均消费 GMS/TRADE/UMS 快照 | 页面组装与 HOME 图表规则 |
| HOME 日快照 | 日订单指标由 TRADE、新会员数由 UMS、库存估值由 GMS 契约提供 | `OmsDailySummary` 的补偿、写入、均值和告警仍为 HOME 自有行为 |

静态复核确认：FIN 没有直接 Mapper 或共享 Entity import；HOME 仅保留 `OmsDailySummary` Entity/Mapper，它在归属计划中是 HOME 自有读模型。HOME 源码中不再出现 `ums_member`、`oms_order` 或 `gms_` 的跨所有者表读取。

## 结构门禁与剩余债务

`scripts/architecture-scan.sh --check-new` 结果：Controller 直接 Mapper import 为 0，跨 Feature `ServiceImpl`/Mapper import 为 0，`platform -> feature` import 为 0，新增违规为 0。

P2.6 已在 P2 验收后独立消除 `OmsOrderController` 的既有 TRADE → FIN 实现 import；
当前架构扫描的跨 Feature 实现 import 为 0。

仍需继续追踪的债务是：全仓有 73 个 Feature 文件导入共享 `com.money.entity`。这不是
73 项跨域违规：多数是数据所有者内部持久化实现。Entity 的逻辑归属及未来场景 DTO
优先级见 `MoneyPOS-Entity-Ownership-and-DTO-Plan.md`；P2 没有授权物理迁移 Entity 包。

## 验证

- FIN/HOME 专项集成回归：`FinanceFeatureIntegrationTest`、`HomeCountSnapshotCharacterizationTest`；
- 隔离 `money_pos_test` 上的全量 Maven 测试；
- `mvn -q package -DskipTests`；
- `scripts/architecture-scan.sh --check-new`；
- `git diff --check`。

所有验证均应在对应的 P2.5 本地提交前通过。路由、页面字段、数据库表、Flyway 和事务边界未修改。

## P2 后续建议

后续架构工作应以独立任务处理共享 Entity 物理归属，优先针对新出现的跨域场景增加窄契约，而不是为移动包名进行大范围重构。既有 TRADE → FIN 控制器兼容调用已由 P2.6 关闭。
