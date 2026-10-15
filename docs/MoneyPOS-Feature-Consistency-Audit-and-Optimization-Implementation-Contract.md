# MoneyPOS 业务 Feature 一致性审查与渐进优化实施契约

> 状态：**已确认，按阶段实施**；确认日期：2026-10-15；适用分支：`dev`。

## 1. 目标

在继续维持单一 `money-app-biz` Maven 业务模块的前提下，逐一审查 GMS、UMS、TRADE、FIN、HOME 与 SYS 的
业务边界和实现一致性；仅对审查已证实、可独立回归的维护问题进行最小优化。目标是让所有 Feature 遵循同一套
所有权、契约、事务、幂等、权限、异常和测试证据标准，而不是重做架构或追求形式上的统一。

## 2. 已冻结的架构与业务约束

1. `money-app-biz` 继续是单一 Maven 业务模块；不得以本工作拆分 GMS、UMS、TRADE、FIN、HOME 或 SYS 为新的 Maven 模块。
2. Feature 之间继续只通过 `money-app-api` 的 Entity-free DTO、快照、查询或命令契约协作；不得新增跨 Feature
   Entity、Mapper、`ServiceImpl` 或内部辅助类依赖。
3. 结算、退款、会员提货和权益操作继续在单一 Spring 容器、单一数据源和既有本地 Spring 事务中完成；不得改变
   事务所有者、支付/库存/资产写入顺序或既有金额公式，除非后续单独冻结业务规则。
4. 不重写历史数据，不批量移动 Mapper，不改公开路由、响应字段、Flyway 历史或已验收的打印/钱箱/Windows 运行行为，
   除非对应最小切片明确证明必要性并包含兼容与回归方案。
5. Boot 3/Jakarta、SQLite 迁移、启动优化、无证据代码删除和 Maven Feature 物理拆分继续冻结。

## 3. 审查顺序与依赖

```text
GMS → UMS → TRADE → FIN → HOME → SYS 横切复核
        └──── TRADE 的商品、库存、会员输入边界 ────┘
                           └── FIN / HOME 只读投影 ──┘
```

| 阶段 | Feature | 先后理由 | 本阶段审查的主问题 |
| --- | --- | --- | --- |
| FCO-1 | GMS | 商品、品牌、价格和库存是后续交易与权益的上游事实。 | 商品/套餐/价格、库存命令、库存单据与分析读模型的所有权和一致性。 |
| FCO-2 | UMS | 依赖稳定的商品品牌与价格边界，并向交易提供会员资产和权益。 | 会员、余额、券、充值和权益账本的状态、逆操作与审计。 |
| FCO-3 | TRADE | 统一消费 GMS/UMS 的稳定契约，承担结算、退款和单据事务。 | 交易编排、幂等、支付、退款、打印和跨域命令协调。 |
| FCO-4 | FIN | 只消费上游的原子读模型。 | 报表口径、退款等价状态、非商品收款和读侧组合。 |
| FCO-5 | HOME | 只消费上游读模型并拥有自己的日快照。 | 首页指标、趋势、快照刷新和读写分离。 |
| FCO-6 | SYS | 作为字典、策略、权限、打印和运行配置的横切复核。 | 各 Feature 对 SYS 的稳定使用方式及配置边界。 |

## 4. 每个 Feature 的固定审查产物

每个 FCO 阶段必须先形成只读审查记录，且至少包含：

1. 数据表、Flyway、Entity、Mapper 与应用服务的实际所有权地图；
2. HTTP 入口、写命令、查询读模型和对外 `money-app-api` 契约的调用链；
3. 事务边界、一致性条件、幂等键、并发/原子 SQL、异常与权限语义；
4. 与其他 Feature、SYS、打印和运行时能力的依赖方向；
5. 现有测试、实际已执行验证证据和未覆盖的风险；
6. 区分为“已确认约束”“已确认问题”“可选建议”的结论；
7. 仅列出可以独立实施、可回归、不会扩大业务规则的最小优化候选。

审查本身不得夹带生产代码重构。没有已确认问题或可量化收益时，应明确记录“无优化切片”，并进入下一 Feature。

## 5. 渐进实施规则

1. 每个 Feature 固定执行 `审查 → 最小优化设计/验收 → 单一切片实施 → 验证/提交/同步`，不得跨 Feature 混合改造。
2. 优化切片只能解决该 Feature 审查中已记录的问题；如触及业务规则、金额公式、公开 API、迁移、历史数据或
   TRADE 事务所有权，必须先获得单独的决策合同。
3. 每个代码切片保持可回退、可审阅和可独立验证。至少运行受影响的集成回归、`mvn -q package -DskipTests`、
   架构门禁与 `git diff --check`；切片收口按仓库既有隔离数据库协议运行全量回归。
4. 完成每个切片后更新当前阶段记录和 Repository Handoff，提交、推送 `dev`，并确认
   `dev...origin/dev` 无 ahead/behind 后才开始下一切片。

## 6. 统一判定标准

以下是审查标准，不预设所有 Feature 必须具有相同类数量、包结构或实现风格：

| 维度 | 一致性要求 |
| --- | --- |
| 所有权 | 写入由数据所有 Feature 完成；跨 Feature 只传契约数据。 |
| 命令与查询 | 写命令明确拥有事务；读模型不借查询端写数据。 |
| 可靠性 | 可重试写操作有稳定幂等键或数据库原子守卫；失败不会留下跨域半完成状态。 |
| 兼容性 | 已发布路由、响应、数据和已冻结公式保持不变，除非明确批准。 |
| 安全与审计 | 写入口有相称权限、操作日志或业务流水；打印等后置副作用不反转已成功交易。 |
| 验证 | 测试应覆盖实际所有权边界、正常路径、重复请求和关键失败/退款逆路径。 |

## 7. 首个行动

开始 **FCO-1：GMS 一致性只读审查**。范围包括商品目录、品牌/分类、价格/套餐、库存命令、库存单据、库存流水、
库存分析，以及 GMS 向 UMS、TRADE、FIN、HOME 暴露的契约。输出审查记录后，才决定是否存在 GMS 最小优化切片。

**完成记录（2026-10-15）：** FCO-1 审查已形成
`MoneyPOS-FCO-1-Gms-Consistency-Audit.md`。GMS-OPT-1 已按
`MoneyPOS-GMS-OPT-1-Turnover-Snapshot-Read-Write-Separation-Implementation-Contract.md` 完成：周转预警查询与导出
不再写日快照；GMS 自有命令在应用就绪和五分钟 fixed-delay 调度时写入当天快照，且同 JVM 不重叠执行。周转公式、
路由、响应、快照表和历史数据均未变更。隔离 `money_pos_test` 的专项 3 项测试、全量 `mvn -q test`、
`mvn -q package -DskipTests`、两项架构扫描和 `git diff --check` 均通过。

下一动作：在 GMS 范围内单独冻结并实施 **GMS-OPT-2**；不得与 GMS-OPT-3 或 GMS-OPT-4 混合。

**完成记录（2026-10-15）：** GMS-OPT-2 已按
`MoneyPOS-GMS-OPT-2-Manual-Outbound-Atomic-Stock-Guard-Implementation-Contract.md` 完成。手工报损出库现在使用
既有 `UPDATE ... WHERE stock >= qty` 原子条件更新；条件失败时整张单据回滚，成功时在同一事务读取实际余额写入明细和
库存流水快照。入库、盘点、套餐穿透语义、路由、单据结构、成本公式和迁移均未变更。隔离 `money_pos_test` 的专项
5 项测试、全量 `mvn -q test`、`mvn -q package -DskipTests`、两项架构扫描和 `git diff --check` 均通过。

下一动作：在 GMS 范围内单独冻结并实施 **GMS-OPT-3**；不得与 GMS-OPT-4 混合。

**完成记录（2026-10-15）：** GMS-OPT-3 已按
`MoneyPOS-GMS-OPT-3-Goods-Deletion-Integrity-Implementation-Contract.md` 完成。删除会拒绝仍被保留套餐引用的子件；
成功删除会清理既有价格/主件配方并回收原品牌、分类的商品计数。同批删除套餐主件及子件仍可完成；库存、订单、单据、
会员权益历史关联既未被删除，也未新增限制。隔离专项 3 项测试、全量 `mvn -q test`、`mvn -q package -DskipTests`、
两项架构扫描和 `git diff --check` 均通过。

下一动作：盘点并冻结 **GMS-OPT-4** 的现有菜单/角色权限映射；不得擅自创造权限编码或扩大既有角色授权。

**完成记录（2026-10-15）：** GMS-OPT-4 已按
`MoneyPOS-GMS-OPT-4-Route-Permission-Alignment-Implementation-Contract.md` 完成。商品导入复用 `gmsGoods:add`，
商品模板/导出及两类分析读取/导出复用 `gmsGoods:list`，品牌策略读写复用 `gmsBrand:list/edit`。未新增权限、菜单、
迁移或角色授权。路由权限契约测试、隔离全量 Maven 回归、打包、两项架构扫描与空白检查均通过。

**完成记录（2026-10-15）：** FCO-2 UMS 只读审查已形成
`MoneyPOS-FCO-2-Ums-Consistency-Audit.md`。下一动作是冻结单一 UMS 最小优化设计，建议先处理不改变业务规则的
UMS-OPT-1 资金流水权限对齐；UMS-OPT-2 会员注销资产守卫须先确认业务规则。

**完成记录（2026-10-15）：** UMS-OPT-1 已按
`MoneyPOS-UMS-OPT-1-Member-Log-Permission-Alignment-Implementation-Contract.md` 完成。`/ums/member-log` 现复用
`umsMember:list`，未新增权限、菜单、迁移或角色授权。路由契约测试、隔离全量 Maven 回归、打包、两项架构扫描与
`git diff --check` 均通过。UMS-OPT-2 继续等待“可注销会员”的资产/权益状态业务规则确认。
