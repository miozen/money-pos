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
| **AD-2** | 共享 Entity 物理归属收敛 | **待设计** | 将目前逻辑归属已明确、但仍放在共享 `com.money.entity` 的类型逐个迁到所有者持久化边界或以所有者 DTO 替代跨域暴露。 | 当前 73 个 Feature 文件 import 共享 Entity 只是报告指标，不可按数量机械迁移。 |
| AD-2.1 | 共享 Entity 消费者再盘点与首切片选择 | 待实施 | 对每个 import 标记“本域持久化合法 / 跨域泄露 / 兼容桥”，并选择一个低风险、单一所有者的实体切片。 | 更新归属表，证明没有 Mapper XML、序列化、事务或跨域调用遗漏。 |
| AD-2.2 | 首个 Entity 物理归属迁移 | 受 AD-2.1 约束 | 只迁移一个已确认所有者和消费者面都可控的 Entity/Mapper/DTO 组合。 | 不改表/Flyway/外部 API；其余消费者通过窄契约；全量回归。 |
| AD-2.3 | Entity 跨域门禁升级 | 受 AD-2.2 约束 | 仅在本域/跨域分类可信后，让扫描器阻止**新增跨域** Entity 契约，同时继续允许所有者内部持久化使用。 | 不能把全部 73 项直接设为失败规则。 |
| **AD-3** | API/实现类型泄露复核 | **实施中** | 清除跨 Feature 服务签名、Controller 或 DTO 中暴露的实现类嵌套类型及可能遗留的 `IService<Entity>` 兼容面。 | AD-3.1～AD-3.3 已关闭；仅剩 GMS→POS 商品实体查询切片。 |
| AD-3.1 | 会员画像实现类型泄露 | **已关闭** | `UmsMemberService.getTop20Goods()` / `UmsMemberController` 已改为 API 顶层 `MemberGoodsRankVO`，不再暴露 `UmsMemberServiceImpl` 嵌套类型。 | 保持排行榜路由与 `goodsName`、`buyCount` 字段，并已补接口回归。 |
| AD-3.2 | 现存跨域 `IService<Entity>` 再审计 | **已关闭** | 已盘点 20 个接口：无 UMS/TRADE 跨域调用，发现 SYS 字典实体读取及 GMS→POS 商品通用查询两处真实风险。见 `MoneyPOS-AD-3.2-IService-Entity-Call-Audit.md`。 | 调用矩阵已固化；不为包名整洁批量改造。 |
| AD-3.3 | SYS 字典实体读取收敛 | **已关闭** | TRADE/GMS/UMS 已改用既有 `SysDictDetailService.getValueToCnDescMap()`，不再接收 `SysDictDetail` 或直接使用其 Mapper。见 `MoneyPOS-AD-3.3-Sys-Dictionary-Read-Contract-Migration.md`。 | 保持支付方式、订单状态、会员类型及 Excel 显示口径；SYS 管理端不动。 |
| AD-3.4 | GMS→POS 商品搜索快照设计 | 待设计 | 为 `GoodsPosFacade` 的 POS 商品搜索设计 GMS 所有者查询契约，去除 `GmsGoodsService.lambdaQuery()`。 | 不合并 SYS 价格策略与 GMS 商品快照；先固定 `/gms/goods/pos-search` 字段和搜索口径。 |
| **AD-4** | Maven 物理模块化重新评估 | **受前置条件约束** | 未来再评估 GMS/UMS/TRADE 是否能从 `money-app-biz` 拆出；当前结论仍为“暂不拆分”。 | 必须先满足 Entity-free 契约、无 UMS↔TRADE 循环、候选模块独立 `test-compile` 价值及 Spring 装配验证；见阶段 5 决策。 |
| **AD-5** | 架构门禁接入 CI | **待设计** | 把现有 `scripts/architecture-scan.sh --check-new` 纳入可重复的 CI/构建检查。 | 先确认现有 CI、失败策略与开发流程；不得把报告型共享 Entity 指标误接为阻断。 |
| **AD-6** | Java 17 源码基线升级评估 | **待设计** | 评估从 Maven Java 8 编译目标升级的收益、依赖兼容和发布风险。 | 独立于 P2/Entity 迁移；未完成前，主模块继续保持 Java 8 源码语法。 |

## 环境验收待办（不是源码架构改造）

| 编号 | 事项 | 状态 | 完成条件 |
| --- | --- | --- | --- |
| ENV-1 | Windows 打包版嵌入式 MariaDB | 环境验收待办 | 在含 `mysqld.exe`、打包目录和既有 `--app.home` 的 Windows 环境验证首次初始化、实例复用、端口冲突及关闭流程。 |
| ENV-2 | 小票/钱箱硬件 | 环境验收待办 | 使用兼容默认打印机验收收银和交接班小票；退款小票目前没有产品入口，须另立功能需求。 |

## 推荐执行顺序

1. **AD-3.4**：设计 GMS→POS 商品搜索快照，收敛通用商品实体查询。
2. **AD-2.1**：以当前代码而非历史数量重建共享 Entity 消费矩阵，并选首个物理归属迁移。
3. 依赖 AD-2 结果实施 AD-2.2/AD-2.3；随后才讨论 AD-4 的物理模块化。
4. AD-5 与 AD-6 分别需要工程治理和平台升级的独立授权。

## 当前下一最小任务

**AD-3.4：盘点并设计 GMS→POS 商品搜索快照，收敛通用商品实体查询。**
