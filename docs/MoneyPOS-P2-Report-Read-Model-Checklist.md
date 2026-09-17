# MoneyPOS P2：FIN/HOME 报表读模型收敛清单

## 目标与边界

P2 将 FIN 与 HOME 的跨域报表读取逐步替换为场景化只读快照。目标是让报表调用方不再直接依赖其他 Feature 的 Entity、Mapper 或实现服务，同时保持既有 SQL 口径、图表字段、时间范围和 HOME 日汇总写入时机。

P2 不移动表、Flyway、Entity 物理包或 API 路由；不把报表查询拆成分布式服务；不改变收银、库存、会员资产的事务边界。共享 Entity 数量继续仅作追踪，不作为完成标准。

## 实施顺序

- [x] P2.0 建立 FIN/HOME 报表读模型调用面基线，详见 `MoneyPOS-P2.0-Report-Read-Model-Inventory.md`。
- [x] P2.1 已迁移 HOME 库存估值：GMS 提供单值 `InventoryValuationQuery`，替换 `HomeServiceImpl` 与 `DecisionEngineServiceImpl` 的全部三处库存估值读取；验证首页 `/home/count`、HOME 日汇总和统计服务均保持相同库存金额及原有计算口径。
- [x] P2.2 已完成 HOME 销售/图表快照设计：TRADE 将提供按既有口径分开的首页区间订单汇总、日快照输入、综合大盘区间汇总、趋势和品牌营收只读模型；UMS 将提供会员等级分布快照；HOME 保留页面组装和 `OmsDailySummary` 写入。详见 `MoneyPOS-P2.2-Home-Read-Snapshot-Design.md`。
- [x] P2.2.1 已迁移服务级首页区间订单汇总：TRADE `HomeOrderReadQuery` 返回订单数、销售额、成本和利润快照；`HomeService.homeCount()` 不再依赖 `OmsOrder`、`QueryWrapper` 或 `OmsOrderMapper`，今日、月、年、总计的右开时间范围和现有金融有效状态集保持不变。
- [ ] P2.3 迁移 FIN 财务大盘读模型：按“订单/支付”“库存单据”“会员资产”三组快照替换 `FinanceDashboardServiceImpl` 的跨域 Mapper、Entity 和 `UmsMemberService` 读取；保持资产、收入、渠道与七日趋势口径。
- [ ] P2.4 迁移 FIN 专项报表：交接班、利润、营销复盘、风控、经营分析与瀑布流 SQL 分别收敛为 TRADE/GMS/UMS 所有者查询；不把不同财务口径强行合并。
- [ ] P2.5 验收与复核：增加 FIN/HOME 集成回归，运行全量 Maven 测试、构建和架构门禁；复核剩余跨 Feature 实现导入与共享 Entity 归属。

## 完成定义

每个切片必须同时满足：

1. 调用方只依赖 `money-app-api` 的报表快照/查询契约；
2. 查询所有者仍在拥有数据写入责任的 Feature 内部使用 Mapper/Entity；
3. 同时间范围、状态过滤、租户过滤和金额公式下，接口输出保持兼容；
4. 不新增跨 Feature 实现包 import，且全量验证通过。

## 当前最小任务

**P2.2.2：设计并迁移 HOME 日快照和综合大盘的 TRADE 订单读取；保留 `OmsDailySummary` 写入、补偿和警报时机。**
