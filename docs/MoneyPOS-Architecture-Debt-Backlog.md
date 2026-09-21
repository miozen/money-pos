# MoneyPOS：后续架构债务编号清单

## 使用方式

本清单从 P2.6 之后开始维护。历史阶段的 `P0`、`P1`、`P2` 已各自收口，故不再把新的
独立债务误编号为 P2.7；统一使用 `AD`（Architecture Debt）编号。每个实施任务必须先
完成对应的“盘点/设计”子项，再以独立提交、回归、打包、架构门禁和台账记录收口。

状态说明：

- **已关闭**：已实施并验收，不应重新作为待办。
- **待设计**：确认存在，但尚未授权改变实现。
- **待实施**：设计完成且可作为下一最小代码切片。
- **受前置条件约束**：不是当前可直接开始的源码任务。
- **环境验收待办**：需要 Windows、硬件或发布环境，不能以代码测试替代。

## 已关闭的相关工作

| 编号 | 事项 | 状态与证据 |
| --- | --- | --- |
| P1 | GMS/UMS/TRADE 的既定跨域场景契约收敛 | 已关闭；见 `MoneyPOS-P1.6-Call-Surface-Review.md`。 |
| P2 | FIN/HOME 报表读模型收敛 | 已关闭；调用方不再直接读取其他所有者的 Entity、Mapper 或实现服务。见 `MoneyPOS-P2-Final-Acceptance-Review.md`。 |
| P2.6 | TRADE `OmsOrderController` → FIN 兼容调用 | 已关闭；提交 `0a734bb`，扫描的跨 Feature 实现 import 为 0。 |
| AD-0 | `DecisionEngine` 的跨所有者**读取**边界 | 已关闭；订单、库存估值、会员日计数都已经 TRADE/GMS/UMS 查询契约取得。 |

`AD-0` 不等于 `DecisionEngine` 的写入治理完成：其 HOME 自有读模型写入仍在下列
`AD-1` 的范围内。

## 活跃架构债务

| 编号 | 事项 | 状态 | 目标与边界 | 前置/验收 |
| --- | --- | --- | --- | --- |
| **AD-1** | HOME `DecisionEngine` 写入边界治理 | **已关闭** | `OmsDailySummary` 的补偿、当日重算、幂等和告警读取已分为 HOME 命令/读模型责任；不把 HOME 快照写入交给 TRADE。 | 定时刷新已接管写入，GET 已为纯读。 |
| AD-1.1 | `DecisionEngine` 写入行为盘点与特征化 | **已关闭** | 已盘点 `compensateSnapshots(7)`、`generateDailySnapshot(today)`、查询后 update/insert、告警均值读取的调用时序及竞争窗口。见 `MoneyPOS-AD-1.1-DecisionEngine-Write-Characterization.md`。 | 已增加顺序重复读取及七日补偿边界特征测试；唯一索引、无锁/版本的并发窗口已记录，未改变数据库或 HTTP 路由。 |
| AD-1.2 | HOME 快照命令模型设计 | **已关闭** | 已确定 HOME 内部 assembler/command/writer/query 分离、已有唯一键上的原子 insert-if-absent/upsert，以及分阶段 GET 去写迁移。见 `MoneyPOS-AD-1.2-Home-Snapshot-Command-Design.md`。 | 不新增 Flyway；最终刷新策略会改变数据新鲜度，保留为用户授权点。 |
| AD-1.3 | HOME 写入边界实施与验收 | **已关闭** | AD-1.3a 已实施命令提取与原子写入；AD-1.3b 已采用启动即刷新、每五分钟定时刷新和并发闸门，GET 已纯读。见 `MoneyPOS-AD-1.3a-Home-Snapshot-Command-Implementation.md`、`MoneyPOS-AD-1.3b-Home-Scheduled-Snapshot-Refresh.md`。 | HOME 控制器、快照补偿、并发/重复请求、全量测试与架构门禁。 |
| **AD-2** | 共享 Entity 物理归属收敛 | **实施中** | 将目前逻辑归属已明确、但仍放在共享 `com.money.entity` 的类型逐个迁到所有者持久化边界或以所有者 DTO 替代跨域暴露。 | 当前 73 个 Feature 文件 import 共享 Entity 只是报告指标，不可按数量机械迁移。 |
| AD-2.1 | 共享 Entity 消费者再盘点与首切片选择 | 已关闭 | 已按当前源码区分本域持久化、跨域泄露和兼容桥，并选定 TRADE `OmsRefundIdempotent`。见 `MoneyPOS-AD-2.1-Shared-Entity-Consumer-Inventory.md`。 | 已复核 Mapper XML、序列化、事务和跨域调用面；扫描继续报告，不机械阻断。 |
| AD-2.2 | 首个 Entity 物理归属迁移 | 已关闭 | 已将 TRADE `OmsRefundIdempotent` 移至所有者持久化 entity 包，并更新 Mapper/guard 导入。 | 未改表/Flyway/外部 API；重复/完整/部分退款和全量回归通过。 |
| AD-2.3 | Entity 跨域门禁升级 | 已关闭 | 已设计以 Entity 所有权、精确兼容桥基线及通配符实际类型审计阻止**新增跨域** Entity 契约。见 `MoneyPOS-AD-2.3-Shared-Entity-Additions-Only-Gate-Design.md`。 | 不把全部剩余共享 Entity import 直接设为失败规则；实施须独立切片。 |
| AD-2.3.1 | Entity 跨域 additions-only 门禁实施 | 已关闭 | 已将 Entity 所有权、精确跨域桥与通配符路径数据化，并接入 `architecture-scan.sh --check-new`；脚本夹具覆盖合法本域、既有桥、新跨域、新通配符、通配符新增跨域和未登记 Entity。 | 默认报告仍不按 74 个文件总数失败；新非所有者/未登记 Entity、以及新通配符失败。 |
| AD-2.4 | 第二个共享 Entity 物理归属切片选择 | 已关闭 | 已选择 GMS `GmsTurnoverWarningSnapshot`，并确认其 GMS 服务/Mapper、无 Entity 外泄、Flyway 唯一键与必补回归。见 `MoneyPOS-AD-2.4-Second-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 AD-2.3.1 基线代替契约治理。 |
| AD-2.5 | GMS 周转预警快照 Entity 物理归属迁移 | 已关闭 | 已将 `GmsTurnoverWarningSnapshot` 移到 GMS 持久化 entity 包，更新 GMS 服务/Mapper 导入并增加周转快照回归。 | 未改周转算法、路由、Excel、SYS 策略读取、表/Flyway、唯一键或吞没异常语义。 |
| AD-2.6 | 第三个共享 Entity 物理归属切片选择 | 已关闭 | 已选择 UMS `PosMemberLevel`，并确认其单一 Mapper、表映射、兼容扫描根与必补 CRUD 回归。见 `MoneyPOS-AD-2.6-Third-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 AD-2.3.1 基线代替契约治理。 |
| AD-2.7 | UMS 会员等级 Entity 物理归属迁移 | 已关闭 | 已将 `PosMemberLevel` 移到 UMS 持久化 entity 包，更新遗留 Mapper 泛型导入并增加 CRUD 回归。 | Mapper 与扫描根未移动，表/Flyway/API 未改；Feature 文件扫描保持 73。 |
| AD-2.8 | 第四个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsInventoryOrderDetail`，并确认其 GMS 服务/Mapper、隐式表名推导、无 Entity 外泄与必补 CRUD 回归。见 `MoneyPOS-AD-2.8-Fourth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.9 | GMS 库存单明细 Entity 物理归属迁移 | 已关闭 | 已将 `GmsInventoryOrderDetail` 移到 GMS 持久化 entity 包，更新 GMS 服务/Mapper 导入并增加 CRUD 回归。 | 主单、Mapper 与扫描根未移动，隐式表名/Flyway/API 未改；Feature 文件扫描降至 72。 |
| AD-2.10 | 第五个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsInventoryDocItem`，并确认两个 GMS 服务、遗留 Mapper、显式表名、无 Entity 外泄与必补快照/CRUD 回归。见 `MoneyPOS-AD-2.10-Fifth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.11 | GMS 库存单明细快照 Entity 物理归属迁移 | 已关闭 | 已将 `GmsInventoryDocItem` 移到 GMS 持久化 entity 包，更新两个 GMS 服务/遗留 Mapper 导入并增加入库快照与 CRUD 回归。 | 不移动主单、Mapper 或扫描根，不改表/Flyway/API；所有权登记降至 24。 |
| AD-2.12 | 第六个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 UMS `GmsMemberTransaction`，并确认其单一遗留 Mapper、隐式表名推导、无 Entity 外泄与必补 CRUD 回归。见 `MoneyPOS-AD-2.12-Sixth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.13 | UMS 会员资金流水 Entity 物理归属迁移 | 已关闭 | 已将 `GmsMemberTransaction` 移到 UMS 持久化 entity 包，更新遗留 Mapper 泛型导入并增加 CRUD 回归。 | 不移动 Mapper 或服务，不改隐式表名/Flyway/API；所有权登记降至 23。 |
| AD-2.14 | 第七个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 TRADE `OmsOrderPay`，并确认三个 TRADE 消费者、遗留 Mapper 聚合 SQL、无 HTTP/跨域 Entity 外泄与必补支付快照/CRUD 回归。见 `MoneyPOS-AD-2.14-Seventh-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.15 | TRADE 订单支付明细 Entity 物理归属迁移 | 已关闭 | 已将 `OmsOrderPay` 移到 TRADE 持久化 entity 包，更新三个 TRADE 消费者/遗留 Mapper/测试导入并增加支付快照与 CRUD 回归。 | 不移动 Mapper 或服务，不改隐式表名、聚合 SQL、表/Flyway/API；所有权登记降至 22。 |
| AD-2.16 | 第八个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 HOME `OmsDailySummary`，并确认三个 HOME 消费者、遗留 Mapper 原子 SQL、无 HTTP/跨域 Entity 外泄与必补 Mapper/日快照回归。见 `MoneyPOS-AD-2.16-Eighth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.17 | HOME 每日经营快照 Entity 物理归属迁移 | 已关闭 | 已将 `OmsDailySummary` 移至 HOME 持久化 entity 包，更新三个 HOME 消费者/遗留 Mapper/测试导入，并增加 Mapper CRUD、原子写入与日快照回归。 | 不移动 Mapper 或服务，不改显式表名、原子 SQL、补偿/刷新、表/Flyway/API；所有权登记降至 21，Feature 共享 import 降至 68。 |
| AD-2.18 | 第九个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsGoodsCombo`，并确认四个 GMS 消费者、遗留 Mapper、无 HTTP/跨 Feature Entity 外泄与必补 Mapper/套餐库存回归。见 `MoneyPOS-AD-2.18-Ninth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.19 | GMS 商品套餐明细 Entity 物理归属迁移 | 已关闭 | 已将 `GmsGoodsCombo` 移至 GMS 持久化 entity 包，更新四个 GMS 消费者/遗留 Mapper/测试导入，并增加 Mapper CRUD 与套餐库存回归。 | 不移动 Mapper 或服务，不改 `ASSIGN_ID`、隐式表名、套餐库存命令、表/Flyway/API；所有权登记降至 20，Feature 共享 import 按文件计由 68 降至 67。 |
| AD-2.20 | 第十个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 TRADE `OmsOrderLog`，并确认六个 TRADE 消费者、专用 Mapper、审计不可变语义、无 HTTP/跨 Feature Entity 外泄与必补 Mapper/订单审计日志回归。见 `MoneyPOS-AD-2.20-Tenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.21 | TRADE 订单审计日志 Entity 物理归属迁移 | 已关闭 | 已将 `OmsOrderLog` 移至 TRADE 持久化实体包，更新专用 Mapper、六个 TRADE 消费者，并增加 Mapper 插入/读回与订单审计日志回归。 | 保持显式表名、`BaseEntity` / `ASSIGN_ID`、审计日志不可修改/删除语义、表/Flyway/API/事务不变；所有权登记降至 19，Feature 共享 import 降至 64。 |
| AD-2.22 | 第十一个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsInventoryOrder`，并确认专用 Mapper、本域服务/泛型、三个库存命令、无 HTTP/跨 Feature Entity 外泄与必补 Mapper/库存单主流程回归。见 `MoneyPOS-AD-2.22-Eleventh-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；不能以扩展 additions-only 基线代替契约治理。 |
| AD-2.23 | GMS 库存单主表 Entity 物理归属迁移 | 已关闭 | 已将 `GmsInventoryOrder` 移至 GMS 持久化实体包，更新专用 Mapper、服务接口/实现，并增加 Mapper CRUD 与入库/盘点/报损主流程回归。 | 保持 `ASSIGN_ID`、隐式表名、本域 `IService`、库存/成本/流水语义、表/Flyway/API/事务不变；所有权登记降至 18，Feature 共享 import 降至 62。 |
| AD-2.24 | 第十二个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 SYS `Provinces`，并确认单 Mapper、本域服务泛型、`SelectVO` HTTP 契约、只读缓存、Flyway 种子表与 tenant-ignore 约束。见 `MoneyPOS-AD-2.24-Twelfth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；历史 `BaseEntity`/表列不对称只能特征化，不能趁迁移修复。 |
| AD-2.24.1 | Provinces 只读映射修复设计 | 已关闭 | AD-2.25 特征化确认 `BaseEntity` / `BaseMapper` 默认查询包含表中不存在的 `id` 与审计列；已选择真实表驱动的窄只读 Mapper/服务模型。见 `MoneyPOS-AD-2.24.1-Provinces-Read-Only-Mapping-Repair-Design.md`。 | 不迁包、不改表/Flyway/tenant-ignore；先收紧只读映射与伪写 API，再恢复物理迁移。 |
| AD-2.24.2 | Provinces 只读映射修复实施 | 已关闭 | `Provinces` 现显式映射真实八列，Mapper 仅保留只读查询，服务移除了通用写继承；已补北京种子、缓存和接口回归。 | 暂留 API 包；未改路由、缓存行为、表/Flyway/tenant-ignore、种子数据或新增写 SQL。 |
| AD-2.25 | SYS 省市区字典 Entity 物理归属迁移 | 已关闭 | 已将验证后的只读 `Provinces` 移至 SYS 持久化实体包，更新 Mapper、服务实现和测试导入，并删除 API 副本及所有权登记。 | Mapper/Controller/扫描根、显式八列查询、Flyway、tenant-ignore、路由与缓存均未改；共享 Feature import 报告保持 62。 |
| AD-2.26 | 第十三个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 UMS `UmsRechargeOrder`，并确认一个遗留 Mapper、两个本域资产服务、同域查询 Controller、显式表映射、无跨域/资源 FQCN 与必补 Mapper/充值红冲/接口回归。见 `MoneyPOS-AD-2.26-Thirteenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；HTTP 输出仅限 UMS 同域，不能以复制 API Entity 或 DTO 重写代替物理归属迁移。 |
| AD-2.27 | UMS 充值凭证 Entity 物理归属迁移 | 已关闭 | 已将 `UmsRechargeOrder` 移至 UMS 持久化实体包，更新 Mapper、两项资产服务和查询 Controller，并补 Mapper 持久化、余额充值/红冲及查询处理器回归。 | 保持显式表名、AUTO ID、单号/资产/日志/红冲事务、路由、鉴权、表/Flyway/索引不变；未新增凭证删除语义；所有权登记降至 16。 |
| AD-2.28 | 第十四个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 SYS `SysPrintConfig`，并确认遗留 Mapper、本域服务接口/实现/Controller、打印服务、固定 ID=1 表映射、无跨域/资源 FQCN 与必补 Mapper/配置接口回归。见 `MoneyPOS-AD-2.28-Fourteenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；硬件动作保留人工验收，不能以迁移改造打印或钱箱语义。 |
| AD-2.29 | SYS 打印配置 Entity 物理归属迁移 | 已关闭 | 已将 `SysPrintConfig` 移至 SYS 持久化实体包，更新 Mapper、服务接口/实现、Controller 和打印服务，并补固定 ID=1 的 Mapper 与配置接口回归。 | 保持显式表名、BaseEntity/INPUT ID、固定 ID=1、种子记录、路由/JSON/异常、表/Flyway 与硬件行为不变；未新增多配置或删除语义；所有权登记降至 15。 |
| AD-2.30 | 第十五个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsInventoryDoc`，并确认遗留 Mapper、四个 GMS 库存/财务快照消费者、无生产跨域 Entity 外泄与必补 Mapper、库存单据、结账/退款及财务快照回归。见 `MoneyPOS-AD-2.30-Fifteenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；保留 `BaseEntity` 的既有 ASSIGN_ID 与表 AUTO_INCREMENT 共存语义，不能借迁移修改库存/财务契约。 |
| AD-2.31 | GMS 库存单据主表 Entity 物理归属迁移 | 已关闭 | 已将 `GmsInventoryDoc` 移至 GMS 持久化实体包，更新遗留 Mapper、四个 GMS 消费者和受影响测试，并补 Mapper、库存单据、结账/退款与财务快照回归。 | Mapper/Controller/DTO、跨域契约、显式表名、BaseEntity/ID 行为、库存事务、快照口径、表/Flyway/唯一键未改；所有权登记降至 14，Feature 共享 import 降至 60。 |
| AD-2.32 | 第十六个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `PosSkuLevelPrice`，并确认遗留 Mapper、七个本域商品价格/快照/Excel 消费者、无生产跨域 Entity 外泄与必补 Mapper/商品/Excel/POS 快照回归。见 `MoneyPOS-AD-2.32-Sixteenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；保留 AUTO ID、价格矩阵合并、既有通配符及 SYS 桥、快照口径、表/Flyway/索引。 |
| AD-2.33 | GMS 商品等级价格 Entity 物理归属迁移 | 已关闭 | 已将 `PosSkuLevelPrice` 移至 GMS 持久化实体包，更新 Mapper、七个本域商品消费者及受影响测试，并补 Mapper、价格矩阵、Excel、结账与 POS 快照回归。 | Mapper/Controller/DTO、跨域契约、显式表名、AUTO ID、价格矩阵合并、通配符/SYS 桥、快照口径、表/Flyway/索引未改；所有权登记降至 13，Feature 共享 import 降至 59。 |
| AD-2.34 | 第十七个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 UMS `UmsMemberBrandLevel`，并确认遗留 Mapper、五个 UMS 矩阵/快照/Excel 消费者、无生产跨域 Entity 外泄与必补 Mapper/导入/HOME/结账/POS 回归。见 `MoneyPOS-AD-2.34-Seventeenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；保留 AUTO ID、唯一键、聚合 SQL、会员矩阵写入、快照口径、表/Flyway。 |
| AD-2.35 | UMS 会员品牌等级 Entity 物理归属迁移 | 已关闭 | 已将 `UmsMemberBrandLevel` 移至 UMS 持久化实体包，更新 Mapper、五个 UMS 消费者和受影响测试，并补 Mapper、会员导入、HOME、Excel、结账与 POS 快照回归。 | Mapper/Controller/DTO、跨域契约、表名、AUTO ID、唯一键、聚合 SQL、会员矩阵语义、表/Flyway 未改；所有权登记降至 12，五个消费者仍各自导入其他共享实体，Feature 共享 import 实测保持 59。 |
| AD-2.36 | 第十八个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsGoodsCategory`，并确认本域 Mapper、分类服务/名称查询、商品结账/Excel 消费者、无生产跨域 Entity 外泄与必补 Mapper/分类树/Excel/快照回归。见 `MoneyPOS-AD-2.36-Eighteenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；保留 `BaseEntity`/ID、图标事务、树/删除守卫、商品数、快照、表/Flyway。 |
| AD-2.37 | GMS 商品分类 Entity 物理归属迁移 | 已关闭 | 已将 `GmsGoodsCategory` 移至 GMS 持久化实体包，更新 Mapper、本域分类/商品消费者和受影响测试，并补 Mapper、分类树/守卫、Excel、结账与 FIN 名称快照回归。 | Mapper/Controller/DTO、跨域契约、表名、BaseEntity/ID、图标事务、树/删除守卫、商品数、快照、表/Flyway 未改；所有权登记降至 11。 |
| AD-2.38 | 第十九个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsBrand`，确认本域 Mapper、品牌服务/名称选择查询、Excel 消费者及无生产跨域 Entity 外泄。见 `MoneyPOS-AD-2.38-Nineteenth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；保留 BaseEntity/ID、logo 事务、商品数、快照、表/Flyway。 |
| AD-2.39 | GMS 商品品牌 Entity 物理归属迁移 | 已关闭 | 已将 `GmsBrand` 移至 GMS 持久化实体包，更新 Mapper、本域消费者和受影响测试，并补 Mapper CRUD 回归。 | Mapper/Controller/DTO、跨域契约、表名、BaseEntity/ID、logo 事务、商品数、快照、表/Flyway 未改；所有权登记降至 10。 |
| AD-2.40 | 第二十个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 GMS `GmsStockLog`，确认库存命令、查询、分析、遗留服务/Controller 均为本域使用。见 `MoneyPOS-AD-2.40-Twentieth-Entity-Slice-Selection.md`。 | 不在选择任务中移动 Entity；保留库存/成本流水、分页 JSON、表/Flyway。 |
| AD-2.41 | GMS 库存流水 Entity 物理归属迁移 | 已关闭 | 已将 `GmsStockLog` 移至 GMS 持久化实体包，并补 Mapper CRUD、库存命令/查询/分析与 Controller 回归。 | 库存事务、流水公式、路由/JSON、表/Flyway 与跨域契约未改；所有权登记降至 9。 |
| AD-2.48 | 第二十四个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已选择 SYS `SysBrandConfig`，并设计 POS 与 GMS Excel 所需的只读品牌券策略契约。 | 见 `MoneyPOS-AD-2.48-Twenty-Fourth-Entity-Slice-Selection.md`。 |
| AD-2.49 | SYS 品牌配置 Entity 物理归属迁移 | 已关闭 | 已将 `SysBrandConfig` 移至 SYS 持久化实体包；POS 与 GMS Excel 改用 Entity-free 品牌券策略查询。 | 保持配置保存、POS 券清零、Excel 动态券列、路由、表/Flyway 与事务语义；所有权登记降至 5。 |
| AD-2.50 | 第二十五个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已重新盘点余下五个共享 Entity，并选择 UMS `PosMemberCoupon`。 | 见 `MoneyPOS-AD-2.50-Twenty-Fifth-Entity-Slice-Selection.md`；选择任务不改生产代码。 |
| AD-2.51 | UMS 会员满减券 Entity 物理归属迁移 | 已关闭 | 已将 `PosMemberCoupon` 移至 UMS 持久化实体包；TRADE 与打印改用 Entity-free 的券钱包/计数契约。 | 保留发券、FIFO 核销、退款恢复、充值红冲、卡包、打印、表/Flyway 与事务语义；所有权登记降至 4。 |
| AD-2.52 | 第二十六个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已重新盘点余下四个共享 Entity，并选择 UMS `PosCouponRule`。 | 见 `MoneyPOS-AD-2.52-Twenty-Sixth-Entity-Slice-Selection.md`；选择任务不改生产代码。 |
| AD-2.53 | UMS 满减券规则 Entity 物理归属迁移 | 已关闭 | 已以 UMS 提供的 Entity-free 规则管理命令/查询契约替换遗留 TRADE 服务与 `/pos/couponRule` 的 Entity 暴露，并迁移 `PosCouponRule`。 | 保持管理端路由/JSON、分页、卡包、结账优惠、表/Flyway 与事务语义；所有权登记降至 3、桥降至 0。 |
| AD-2.54 | 第二十七个共享 Entity 物理归属迁移切片选择 | 已关闭 | 已重新盘点余下三个共享 Entity，并选择 TRADE `OmsOrderDetail`。 | 见 `MoneyPOS-AD-2.54-Twenty-Seventh-Entity-Slice-Selection.md`；选择任务不改生产代码。 |
| AD-2.55 | TRADE 订单明细 Entity 物理归属迁移 | 待实施 | 将 `OmsOrderDetail` 迁入 TRADE 持久化边界，更新本域 Mapper、结账、退款、查询、打印消费者与测试。 | 保持结账/退款/库存/订单详情/打印/报表语义、表/Flyway 与事务；预期登记册降至 2，桥保持 0。 |
| **AD-3** | API/实现类型泄露复核 | **已关闭** | 已清除已盘点的跨 Feature 服务签名、Controller/DTO 实现类型及 GMS→POS 通用商品实体查询泄露。 | AD-3.1～AD-3.4.1 已完成；新增泄露须另行编号。 |
| AD-3.1 | 会员画像实现类型泄露 | **已关闭** | `UmsMemberService.getTop20Goods()` / `UmsMemberController` 已改为 API 顶层 `MemberGoodsRankVO`，不再暴露 `UmsMemberServiceImpl` 嵌套类型。 | 保持排行榜路由与 `goodsName`、`buyCount` 字段，并已补接口回归。 |
| AD-3.2 | 现存跨域 `IService<Entity>` 再审计 | **已关闭** | 已盘点 20 个接口：无 UMS/TRADE 跨域调用，发现 SYS 字典实体读取及 GMS→POS 商品通用查询两处真实风险。见 `MoneyPOS-AD-3.2-IService-Entity-Call-Audit.md`。 | 调用矩阵已固化；不为包名整洁批量改造。 |
| AD-3.3 | SYS 字典实体读取收敛 | **已关闭** | TRADE/GMS/UMS 已改用既有 `SysDictDetailService.getValueToCnDescMap()`，不再接收 `SysDictDetail` 或直接使用其 Mapper。见 `MoneyPOS-AD-3.3-Sys-Dictionary-Read-Contract-Migration.md`。 | 保持支付方式、订单状态、会员类型及 Excel 显示口径；SYS 管理端不动。 |
| AD-3.4 | GMS→POS 商品搜索快照设计 | **已关闭** | 已确认既有 `PosGoodsCatalogQuery` 与兼容路由口径不同，并设计独立的 GMS 所有者快照。见 `MoneyPOS-AD-3.4-Gms-Pos-Goods-Search-Snapshot-Design.md`。 | 固定旧路由字段、未分组 SQL 的实际状态口径、原样助记码匹配及 SYS 策略边界。 |
| AD-3.4.1 | GMS→POS 商品搜索快照迁移 | 已关闭 | GMS 通过独立 Entity-free 快照提供遗留 POS 搜索；`GoodsPosFacade` 仅组装旧响应和 SYS 品牌券策略。 | 保持 `/gms/goods/pos-search`；已回归非 `SALE` 的条码/名称、助记码、原样小写关键字、空关键字、价格矩阵及策略关券。 |
| **AD-4** | Maven 物理模块化重新评估 | **受前置条件约束** | 未来再评估 GMS/UMS/TRADE 是否能从 `money-app-biz` 拆出；当前结论仍为“暂不拆分”。 | 必须先满足 Entity-free 契约、无 UMS↔TRADE 循环、候选模块独立 `test-compile` 价值及 Spring 装配验证；见阶段 5 决策。 |
| **AD-5** | 架构门禁接入 CI | **待设计** | 把现有 `scripts/architecture-scan.sh --check-new` 纳入可重复的 CI/构建检查。 | 先确认现有 CI、失败策略与开发流程；不得把报告型共享 Entity 指标误接为阻断。 |
| **AD-6** | Java 17 源码基线升级评估 | **待设计** | 评估从 Maven Java 8 编译目标升级的收益、依赖兼容和发布风险。 | 独立于 P2/Entity 迁移；未完成前，主模块继续保持 Java 8 源码语法。 |

## 环境验收待办（不是源码架构改造）

| 编号 | 事项 | 状态 | 完成条件 |
| --- | --- | --- | --- |
| ENV-1 | Windows 打包版嵌入式 MariaDB | 环境验收待办 | 在含 `mysqld.exe`、打包目录和既有 `--app.home` 的 Windows 环境验证首次初始化、实例复用、端口冲突及关闭流程。 |
| ENV-2 | 小票/钱箱硬件 | 环境验收待办 | 使用兼容默认打印机验收收银和交接班小票；退款小票目前没有产品入口，须另立功能需求。 |

## 推荐执行顺序

1. **AD-2.55**：TRADE `OmsOrderDetail` Entity 物理归属迁移。
2. 依赖 AD-2 结果实施门禁；随后才讨论 AD-4 的物理模块化。
3. AD-5 与 AD-6 分别需要工程治理和平台升级的独立授权。

## 当前下一最小任务

**AD-2.55：TRADE 订单明细 Entity 物理归属迁移。**
