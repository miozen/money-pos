# MoneyPOS ME-1：品牌会员权益实施与验收清单

> 建立日期：2026-09-23；源码基线：`dev`。本清单落实已冻结的 QUANTITY、AMOUNT、TARGET 规则；不新增 Maven module，不改 Boot/SQLite，不重构普通 POS。

## 实施纪律

- 每个编号切片只交付一个可验证写侧或读侧能力；完成前必须更新本清单的结果、风险与唯一下一项。
- 新增跨 Feature 调用只能使用 `money-app-api` 中的场景契约；不得引入跨 Feature Mapper、Entity 或实现类依赖。
- 普通 POS 的 `IMMEDIATE` Checkout、普通退款、商品销量和现有库存语义是回归基线，不得因权益场景改变。
- 所有金额使用 `BigDecimal`；档位金额仅是配置值，绝不能作为业务编码或等级比较依据。
- 每个写命令必须具备请求幂等键、事务边界和操作审计；金额/数量余额必须由不可变流水和原子更新共同保护。

## 总览

| 编号 | 目标 | 状态 | 验收结论 | 下一项 |
| --- | --- | --- | --- | --- |
| ME-1.0 | 冻结模型、范围和实施顺序 | 完成 | 三类场景、退款边界、收入确认和不降级规则均已确认 | ME-1.1 |
| ME-1.1 | 品牌权益档位与 UMS 账本基础 | 完成 | Flyway、3 项账本专项、17 项既有 Checkout 回归、编译和静态架构门禁均通过 | ME-1.2 |
| ME-1.2 | QUANTITY 延迟履约购买与提货 | 完成 | `e44c6e9` 已完成实现，`e3928a7` 记录收口；隔离全量回归、打包和架构门禁通过，且 `dev...origin/dev` 为 0/0。 | ME-1.3 |
| ME-1.3 | AMOUNT 权益包、混合补差与整笔提货退款 | 完成 | `3cffdec` 实现、`f0a9781` 接力记录均已推送；隔离全量回归、打包、架构门禁通过，且 `dev...origin/dev` 为 0/0。 | ME-1.4 |
| ME-1.4 | TARGET 专用结算、进度、补差和人工确认 | 完成 | `3dc88ec` 实现、`4e2ab06` 接力记录均已推送；隔离全量回归、打包和架构门禁通过。 | ME-1.5 |
| ME-1.5 | 会员权益操作化收口 | 本地进行中 | FIN/HOME 投影、读 API、后台页面与 ME-1.5A POS 工作台基础已在本地；后续操作化仍以冻结实施合同为唯一执行合同。 | ME-1.5B |

## ME-1.1：品牌权益档位与 UMS 账本基础

### 范围

1. 新增每品牌权益档位：`tierCode`、名称、`configuredAmount`、`pricingLevelCode`、`rank`、启用状态和排序。
2. 新增 QUANTITY、AMOUNT、TARGET 的主记录与不可变流水持久化模型；仅提供 UMS 内部命令，不接入 UI、POS 或库存。
3. 建立如下约束：
   - 档位编码在品牌内唯一，`rank` 在品牌内唯一；
   - QUANTITY/AMOUNT 余额不能为负；
   - TARGET 进度只由流水增量汇总，`INITIAL_PROGRESS` 必须带来源、操作人、时间、原因；
   - `ums_member_brand_level` 只在候选档位 `rank` 更高时更新。
4. 为后续切片定义中立命令/快照契约；本切片不创建 Controller，避免过早暴露未完成业务。

### 非范围

- 不生成销售订单、非商品凭证、支付、GMS 出入库或 FIN/HOME 数据。
- 不处理套餐、有效期、转让、自动降级、普通退款或页面路由。
- 不修改普通 `CheckoutOrchestrator`。

### 验收清单

- [x] ME-1.1.1 Flyway 可从现有隔离库升级创建全部新表、索引和约束。
- [ ] ME-1.1.2 档位金额可任意配置，例如 660、2700 仅为数据而非枚举。
- [x] ME-1.1.3 AMOUNT/QUANTITY 增减流水与主记录余额在同一事务内更新；负余额被拒绝。
- [x] ME-1.1.4 TARGET 的 `INITIAL_PROGRESS`、销售贡献、补差、豁免和反冲可审计，并正确更新汇总进度。
- [x] ME-1.1.5 更低或相同 `rank` 不会覆盖已有品牌等级；更高 `rank` 才能升级。
- [x] ME-1.1.6 相关集成测试、既有 CheckoutIntegrationTest、编译和架构扫描通过。

## ME-1.2：QUANTITY 延迟履约

### 范围与验收

- 专用入口以 `DEFERRED_QUANTITY` 调用既有校验、定价、订单、会员资产和支付能力；销售收入正常确认，但跳过 GMS `SALE`。
- 建立商品权益、来源订单/明细关联、提货单和 `MEMBER_PICKUP` / `MEMBER_PICKUP_RETURN` 库存命令。
- 提货必须原子完成权益扣减、实际库存扣减和流水；调整不得令剩余权益为负。
- 验收：购买无库存变化；提货才出库且无二次收入；未提权益退款不回库，已提实物退货才回库。

### 实施清单（唯一执行顺序）

> 本节是 ME-1.2 的唯一实施与验收合同。编号是同一 ME-1.2 任务的内部完成条件，不是可独立提交、
> 可跳过或可在未验证时宣称完成的子任务。除本节明确内容外，不得混入 AMOUNT、TARGET、页面、FIN/HOME
> 投影、物理模块拆分或普通 POS 语义变更。

- [x] **ME-1.2.1 数据与契约。** 以 Flyway 建立 TRADE 提货单/明细及请求号唯一约束；在 `money-app-api`
  定义 Entity-free 的 UMS 权益读取/扣减与 GMS 提货扣库命令。所有命令须有请求号、事务边界、审计来源。
- [x] **ME-1.2.2 原生 GMS 提货路径。** 抽取普通销售和会员提货共用的原子扣库/套餐展开算法；提货从入口开始
  即写 `MEMBER_PICKUP` 凭证与流水，绝不得先写 `SALE`/`SALE_OUT` 后再改写类型。
- [x] **ME-1.2.3 延迟购买。** 仅专用入口可调用：复用校验、定价、订单、会员资产和支付；必须关联会员；订单成功后
  按订单明细发放 QUANTITY 权益；不得调用 GMS `SALE`、不得扣实物库存；普通 `/pos/settleAccounts` 保持不变。
- [x] **ME-1.2.4 提货编排。** TRADE 以请求号幂等写提货单；在一个 Spring 事务内校验权益/商品快照、扣 UMS
  权益、调用 GMS `MEMBER_PICKUP`、写提货明细。任一步异常必须回滚全部写入。
- [x] **ME-1.2.5 退款分叉。** 仅为 QUANTITY 订单补齐退款：未提数量权益退款不回库；已提实物退货才经
  `MEMBER_PICKUP_RETURN` 回库。不得让现有普通退款对未曾出库的延迟购买回库存。
- [x] **ME-1.2.6 HTTP 与权限。** 专用购买/提货入口使用 `pos:cashier`；不改变普通结账路由、响应、交易公式。
- [x] **ME-1.2.7 回归。** 在 `money_pos_test` 覆盖购买不扣库、提货才扣库、套餐、权益不足、库存不足回滚、
  购买/提货幂等、未提退款不回库、已提退货回库；同时运行既有 Checkout 全套回归。
- [x] **ME-1.2.8 收口。** 全量测试、打包、架构门禁与空白检查通过；`e44c6e9` 与接力记录
  `99fdce1` 已推送至 `origin/dev`，并已验证 `dev...origin/dev` 无 ahead/behind。

### ME-1.2 收口事实（2026-09-29）

- `e44c6e9` 已实现并提交提货单/明细 Flyway、TRADE 编排、UMS/GMS Entity-free 契约、原生
  `MEMBER_PICKUP` / `MEMBER_PICKUP_RETURN` 库存路径及退款分叉。
- `e3928a7` 已记录收口；隔离完整回归（121 tests）、打包、架构门禁和空白检查均通过，且
  `dev...origin/dev` 为 0/0。

## ME-1.3：AMOUNT 权益包与混合补差

### 范围与验收

- 新增 TRADE 非商品业务凭证及支付明细，承载 `AMOUNT_PACKAGE_PURCHASE` 与 `AMOUNT_PICKUP_SUPPLEMENT`，不伪造 `oms_order`。
- AMOUNT 提货按权益的价格档快照计价；权益抵扣不确认收入，现金/扫码/余额补差确认新增收入并实际出库。
- V1 仅支持整笔提货单退款：恢复权益、原路退补差、GMS `MEMBER_PICKUP_RETURN`、冲回补差收入和成本。
- 验收：150 = 权益 100 + 扫码 50 时，收入只新增 50，商品订单数和商品销量不增加。

### 实施清单（唯一执行顺序）

- [x] **ME-1.3.1 数据与契约。** 以 Flyway 建立 TRADE 非商品业务凭证、凭证支付、AMOUNT 提货及明细；
  在 `money-app-api` 增加 Entity-free 的 AMOUNT 权益读取与扣减边界。凭证请求号唯一，支付与商品订单支付表隔离。
- [x] **ME-1.3.2 权益包购买。** 仅专用入口可为已验证会员按启用品牌档位创建
  `AMOUNT_PACKAGE_PURCHASE` 凭证、支付明细和 AMOUNT 权益；不得创建 `oms_order`、订单明细或库存流水。
- [x] **ME-1.3.3 混合补差提货。** 按权益中冻结的价格等级快照计价，原子扣减权益、写
  `AMOUNT_PICKUP_SUPPLEMENT`（仅补差额支付/收入）、调用原生 `MEMBER_PICKUP` 扣实物库存并写提货明细。
  权益抵扣不作为新增收入，不得写商品订单或商品销量。
- [x] **ME-1.3.4 整笔退款。** 仅允许完整提货单退款；恢复原 AMOUNT 权益、原路冲回补差支付、使用
  `MEMBER_PICKUP_RETURN` 回库，并将补差收入与成本一并冲回。拒绝部分退款及重复退款。
- [x] **ME-1.3.5 HTTP、权限与回归。** 所有专用入口使用 `pos:cashier`；在 `money_pos_test` 覆盖权益包幂等、
  100+50 混合补差、价格快照、余额/库存失败回滚、整笔退款、重复/部分退款拒绝，以及普通 Checkout 回归。
- [x] **ME-1.3.6 收口。** 全量测试、打包、架构门禁和空白检查通过；`3cffdec` 与 `f0a9781` 已推送，
  并已验证 `dev...origin/dev` 无 ahead/behind。

## ME-1.4：TARGET 专用结算与人工升级

### 范围与验收

- TARGET 专用 UI 调用既有 `IMMEDIATE` Checkout；正常订单、支付、库存、成本和收入保持不变。
- 成功后按订单/计划唯一关联写 `SALE_CONTRIBUTION`；补差使用非商品凭证，豁免不产生收入。
- `INITIAL_PROGRESS`、补差、豁免、退款反冲均写进度流水；达标后仍必须人工确认。
- 验收：TARGET 期间采用当前品牌等级价格；确认后才升级；历史退款只转 `REVIEW_REQUIRED`，不自动降级。

### 实施清单（唯一执行顺序）

- [x] **ME-1.4.1 即时结算与贡献关联。** 专用 `/pos/target/settle` 仅编排既有 `IMMEDIATE`
  Checkout，并以计划/订单唯一关联写 `SALE_CONTRIBUTION`；TARGET 商品必须属于计划品牌，普通订单语义不变。
- [x] **ME-1.4.2 进度调整。** 补差以独立非商品凭证及支付明细入账，豁免仅写非收入凭证；二者均以幂等请求号写
  TARGET 进度流水。
- [x] **ME-1.4.3 人工确认与退款。** 达标后由 `/pos/target/confirm` 人工确认并升级；关联销售退款只写
  `REFUND_REVIEW_REQUIRED` 审计并置 `REVIEW_REQUIRED`，绝不自动降级或反向调整历史等级。
- [x] **ME-1.4.4 HTTP、权限与回归。** 所有专用入口使用 `pos:cashier`；隔离专项覆盖即时订单幂等贡献、补差/
  豁免凭证、确认升级与退款复核，并通过全部既有 Checkout 回归。
- [x] **ME-1.4.5 收口。** `3dc88ec` 与接力记录 `4e2ab06` 已推送；全量测试、打包、架构门禁和空白检查通过，
  且推送前 `dev...origin/dev` 为 0/0。

## ME-1.5：报表、页面与收口

### 范围与验收

- FIN/HOME 合并商品订单与非商品凭证的收入/收款/退款投影；商品销量、订单数、商品成本统计不受非商品凭证污染。
- 完成会员权益主页面：会员查询、权益列表、专用购买、提货、调整、TARGET、流水/历史。
- 验收：权限、审计、并发、幂等、FIN/HOME 分项、普通 POS 回归和 Windows 手工冒烟全部通过。

### 历史基础与实施合同

> 原 ME-1.5.0～ME-1.5.5 记录的是当前本地基础实现与自动验证事实，不得被视为操作化完成结论。
> 自 2026-09-29 起，后续 ME-1.5 唯一实施与验收合同为
> [`MoneyPOS-ME-1.5-Member-Benefit-Operationalization-Implementation-Plan.md`](MoneyPOS-ME-1.5-Member-Benefit-Operationalization-Implementation-Plan.md)。
> 其执行顺序为 ME-1.5A～ME-1.5E；ME-1.5A 已在本地完成，当前仅可进入 ME-1.5B。

- [x] **ME-1.5.0 收口复核。** 核对 `108a866`、`dev...origin/dev=0/0` 和 ME-1.4 验证事实；盘点确认既有
  FIN/HOME 读模型只汇总 `oms_order`，非商品凭证尚未进入投影，前端亦没有会员权益主页面。
- [x] **ME-1.5.1 TRADE 投影契约。** 已为 FIN/HOME 提供 Entity-free 的 AMOUNT/TARGET 非商品凭证日维度
  收入、收款、退款快照；收入/收款/退款合并，商品订单数、商品销量和商品成本仍由订单来源专属查询提供。
- [x] **ME-1.5.2 FIN/HOME 接入。** 已将既有 FIN 仪表、支付/退款趋势、渠道构成和瀑布，以及 HOME 日快照、
  计数、综合面板和趋势接入上述快照；未改变公开路由/字段，商品订单数、销量和成本仍不受非商品凭证污染。
- [x] **ME-1.5.3 权益查询与历史 API。** 已在 UMS/TRADE 分别提供会员权益、档位、TARGET 进度流水、提货及
  非商品凭证历史的受权限保护查询；跨 Feature 没有引入 Entity、Mapper 或实现类依赖。
- [x] **ME-1.5.4 权益主页面。** 已接入会员查询、权益列表、专用购买、提货、调整、TARGET 确认及流水/历史；
  所有写操作复用既有专用 API 与新请求号幂等，不改变普通 POS。前端生产构建已通过。
- [ ] **ME-1.5.5（已由操作化合同重排）。** 原自动验收事实保留，但 Windows 手工冒烟、POS 权益操作化、
  权限/菜单、档位配置、最终提交/推送/同步，均以 ME-1.5A～ME-1.5E 的验收条件为准。

## 当前实施结果

### ME-1.5（2026-09-29，本地进行中）

- **ME-1.5A（2026-09-30，本地完成待后续总体验证）**：已把权益页抽为可复用
  `MemberBenefitWorkspace`；POS `HeaderBar` 新增“权益办理”，在主窗口 full-screen dialog 中挂载工作台。
  打开时仅浅拷贝 `currentMember` 作为默认会员，工作台始终可通过既有 `MemberSmartSearch` 重搜/切换；它不调用
  `bindMember`，也不访问 POS 的 `cartList`、`paymentList`、试算或普通结算。后台壳同样复用该 Workspace，且删除
  操作员输入 memberId 的入口。已通过 Node `v20.20.2` Vite 生产构建和 `git diff --check`；Windows/POS 手工状态
  隔离验证留待 ME-1.5E。
- **ME-1.5B（2026-09-30，本地进行中）**：已补齐数量权益购买、TARGET 专用销售结算和 AMOUNT 提货整笔退款的
  Workspace UI/API 路径；商品搜索和扫码始终只写 Workspace `pickupItems`。已新增独立
  `BenefitPaymentEditor`（不访问 POS `paymentList`），但尚未完成把所有既有操作窗统一迁移到它及 B 的专项交互验收；
  因此 B 不能标记完成，且不得进入 C～E。
- 收口复核确认 `108a866` 已将 ME-1.4 文档收口同步至 `origin/dev`，开始时 `dev...origin/dev` 为 0/0；
  Repository Handoff 的阶段字段滞后于 Git，已按仓库事实更正。
- 新增 `FinanceNonProductReceiptQuery` 及日维度快照，由 TRADE 聚合 AMOUNT 的 `net_amount` 和 TARGET 的
  `pay_amount`：正额为收入、代数和为净收款、负额绝对值为退款。没有向 FIN/HOME 暴露凭证 Entity 或 Mapper，
  也没有修改任何商品订单、销量、订单数或成本统计。
- 已通过 `mvn -q -pl qk-money-app/money-app-biz -am test-compile -DskipTests`、隔离
  `FinanceFeatureIntegrationTest`（14 tests，0 failures/errors）及 `git diff --check`。
- ME-1.5.2 将非商品支付按原支付方式/标签合并进 FIN 渠道和趋势；收入正额、净收款和退款分别进入核心指标和
  瀑布。HOME 只将非商品净收款加入营业额/利润，订单数与成本仍完全来自商品订单；HOME 日快照仍只由既有启动/
  每五分钟写入任务刷新，GET 保持纯读。
- 已通过隔离 `FinanceFeatureIntegrationTest`（14 tests，0 failures/errors）和
  `HomeCountSnapshotCharacterizationTest`（7 tests，0 failures/errors）；后者覆盖非商品净收款增加 HOME
  营业额/利润但不增加订单数/成本。
- ME-1.5.3 新增 UMS `/ums/member-benefit/overview`（档位、数量/金额权益、TARGET 计划）与
  `/target-logs`，以及 TRADE `/member-benefit/trade-history`（提货及非商品凭证历史）。所有响应均为 API DTO，
  三条读路由均要求 `umsMember:list`；没有扩大 POS 写权限或暴露持久化 Entity。
- 已通过 `UmsMemberBenefitRouteContractTest`（1 test，0 failures/errors）、
  `mvn -q -pl qk-money-app/money-app-biz -am test-compile -DskipTests` 和 `git diff --check`。
- ME-1.5.4 已新增后台 `/ums/member-benefit` 页面及会员列表“权益中心”入口：支持权益/档位、数量与金额提货、
  金额权益包购买、TARGET 补差/豁免/人工确认、TARGET 审计流水和 TRADE 历史。页面写操作均调用既有专用 API，
  每次操作生成独立 `reqId`；正常 POS 路由与结算没有变动。
- 已通过既有 Node 20 运行时的 `source /home/mio/.nvm/nvm.sh && nvm use 20 && npm run build`（Node `v20.20.2`、npm `10.8.2`）。构建仅报告既有 CSS
  语法、静态/动态导入重复与 bundle 体积警告，没有新增构建错误。
- ME-1.5.5 的自动验收已通过：隔离 `money_pos_test` 上 `mvn -q test` 共 130 tests、0 failures/errors；
  `mvn -q package -DskipTests`、`bash scripts/test-architecture-scan.sh`、
  `bash scripts/architecture-scan.sh --check-new` 和 `git diff --check` 均通过。全量回归首次暴露既有
  `CheckoutIntegrationTest` 的 `REFUND-PARTIAL-<nanoTime>` 库存单号超过 `doc_no varchar(32)`；测试改为
  base36 短后缀后，专项和全量回归均通过，未改变业务代码或规则。
- Windows 手工冒烟、最终提交、推送和同步复核尚未执行；因此 ME-1.5 仍处于进行中，工作区仍为本地未提交状态。

### ME-1.1（2026-09-23）

- 已新增 `V1.0.5__create_member_brand_benefit_ledger.sql`：品牌档位、QUANTITY/AMOUNT 主记录与流水、TARGET 主记录与进度流水；金额档位为 `decimal(12,2)` 配置值，未固化 660/2700 等示例金额。
- 已新增 UMS 内部 `MemberBrandBenefitLedgerCommandHandler` 及实现。数量/金额变动按请求号幂等，以行锁和条件更新拒绝负余额；TARGET 创建总会写 `INITIAL_PROGRESS` 流水；品牌等级仅在候选档位 `rank` 更高时更新。
- 已新增覆盖金额权益幂等/负余额、TARGET 初始进度和等级不降级的集成测试。
- 已通过：受控本机 `money_pos_test` 上的 `MemberBrandBenefitLedgerServiceIntegrationTest`（3 tests，0 failures/errors）与既有 `CheckoutIntegrationTest`（17 tests，0 failures/errors）；其中 Flyway 已将隔离库从 `1.0.4` 升级至 `1.0.5`。同时通过 `mvn -pl qk-money-app/money-app-biz -am compile -DskipTests -q`、`test-compile`、`mvn -q package -DskipTests`、`scripts/architecture-scan.sh --check-new`、`git diff --check`。
- 首次测试失败只是当前 shell 未注入测试变量；完整阅读接力说明后，已按文档规定从 `application-dev.yml` 提取既有本地开发凭据，并以固定 `money_pos_test` URL 受控执行。普通沙箱的 `Operation not permitted` 是本机 3306 网络限制，不是变量或数据库缺失。

ME-1.1 已具备开始 ME-1.2 的前置条件；下一切片仅实现 QUANTITY 延迟履约，不提前混入 AMOUNT 或 TARGET。

### ME-1.3（2026-09-29，本地完成待同步）

- 新增 `V1.0.8__create_member_amount_receipt_and_pickup.sql`，以 TRADE 非商品凭证、独立凭证支付、AMOUNT
  提货和提货明细承载权益包购买、补差和退款审计；没有写入 `oms_order`、订单明细或商品订单支付表。
- 新增 Entity-free AMOUNT 权益、启用档位和余额支付契约。UMS 负责权益余额和余额支付流水，TRADE 编排只读取
  快照并提交命令，GMS 继续独占 `MEMBER_PICKUP` / `MEMBER_PICKUP_RETURN` 实物库存命令。
- `/pos/amount-package/purchase`、`/pickup`、`/pickup-refund` 均受 `pos:cashier` 保护。提货按权益冻结的
  `pricingLevelCodeSnapshot` 计价：权益抵扣不入补差凭证，补差支付才记为新增收入；整笔退款恢复权益、冲回补差
  支付并回库。
- 已通过隔离 `money_pos_test` 专项（`MemberAmountBenefitServiceIntegrationTest` 4 项、路由契约）与
  `CheckoutIntegrationTest`；完整 `mvn -q test` 为 65 个测试类、125 项、零 failures/errors。还通过
  `mvn -q package -DskipTests`、`scripts/test-architecture-scan.sh`、
  `scripts/architecture-scan.sh --check-new` 和 `git diff --check`。

### ME-1.4（2026-09-29，已同步）

- 新增 `V1.0.9__create_member_target_trade_records.sql`：TARGET 即时销售贡献关联、补差/豁免非商品凭证及其独立支付明细。
  贡献以 `(target_plan_id, order_no)` 唯一；不改变普通 `oms_order`、支付、库存、成本或收入写入。
- `/pos/target/settle` 复用原有即时 Checkout；计划会员及计划品牌校验后，才追加 `SALE_CONTRIBUTION`。
  `/supplement`、`/waive`、`/confirm` 均为 `pos:cashier` 专用入口；达到阈值仍须人工确认才升级。
- 关联普通订单的全额或部分退款均只追加 `REFUND_REVIEW_REQUIRED` 流水并置计划为 `REVIEW_REQUIRED`，没有自动降级。
- 已通过隔离专项 `MemberTargetBenefitServiceIntegrationTest`（2 项）和既有账本专项（3 项）；隔离完整
  `mvn -q test` 为 127 tests、零 failures/errors。也已通过 `mvn -q package -DskipTests`、
  `scripts/test-architecture-scan.sh`、`scripts/architecture-scan.sh --check-new` 与 `git diff --check`。
