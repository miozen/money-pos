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
| ME-1.2 | QUANTITY 延迟履约购买与提货 | **本地进行中** | 存在未提交源码；仅编译通过，尚未形成业务验收或回归结论，不能提交、不能接力、不能开始 ME-1.3。 | 仅在本清单 ME-1.2.1～ME-1.2.8 全部闭环后进入 ME-1.3 |
| ME-1.3 | AMOUNT 权益包、混合补差与整笔提货退款 | 未开始 | - | ME-1.4 |
| ME-1.4 | TARGET 专用结算、进度、补差和人工确认 | 未开始 | - | ME-1.5 |
| ME-1.5 | FIN/HOME 投影、查询页面与总体验收 | 未开始 | - | 收口复核 |

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

### 当前本地源码盘点（2026-09-29）

下列内容存在于工作区，**不表示任一验收项完成**：

- 已新增未提交的提货单/明细 Flyway、TRADE Entity/Mapper、DTO，以及 UMS/GMS 的中立契约草案。
- 已新增延迟购买和提货 HTTP 入口及编排，并通过专项隔离数据库回归。
- GMS 从 `MemberPickupStockCommand` 直接进入 `MEMBER_PICKUP` 扣库、凭证和流水，且与销售共享原子扣库/
  套餐展开算法；专项回归断言提货不产生 `SALE` 或 `SALE_OUT`。
- 退款链路通过 UMS 权益命令将退款分配为未提权益取消与已提实物退货；仅后者调用 GMS
  `MEMBER_PICKUP_RETURN`，并以 Flyway 扩展库存单类型长度。

### 当前唯一下一动作

执行 ME-1.2.8 收口：全量测试、打包、架构门禁、文档验收、提交与同步；任何一项缺失均不得宣称 ME-1.2 完成。

## ME-1.3：AMOUNT 权益包与混合补差

### 范围与验收

- 新增 TRADE 非商品业务凭证及支付明细，承载 `AMOUNT_PACKAGE_PURCHASE` 与 `AMOUNT_PICKUP_SUPPLEMENT`，不伪造 `oms_order`。
- AMOUNT 提货按权益的价格档快照计价；权益抵扣不确认收入，现金/扫码/余额补差确认新增收入并实际出库。
- V1 仅支持整笔提货单退款：恢复权益、原路退补差、GMS `MEMBER_PICKUP_RETURN`、冲回补差收入和成本。
- 验收：150 = 权益 100 + 扫码 50 时，收入只新增 50，商品订单数和商品销量不增加。

## ME-1.4：TARGET 专用结算与人工升级

### 范围与验收

- TARGET 专用 UI 调用既有 `IMMEDIATE` Checkout；正常订单、支付、库存、成本和收入保持不变。
- 成功后按订单/计划唯一关联写 `SALE_CONTRIBUTION`；补差使用非商品凭证，豁免不产生收入。
- `INITIAL_PROGRESS`、补差、豁免、退款反冲均写进度流水；达标后仍必须人工确认。
- 验收：TARGET 期间采用当前品牌等级价格；确认后才升级；历史退款只转 `REVIEW_REQUIRED`，不自动降级。

## ME-1.5：报表、页面与收口

### 范围与验收

- FIN/HOME 合并商品订单与非商品凭证的收入/收款/退款投影；商品销量、订单数、商品成本统计不受非商品凭证污染。
- 完成会员权益主页面：会员查询、权益列表、专用购买、提货、调整、TARGET、流水/历史。
- 验收：权限、审计、并发、幂等、FIN/HOME 分项、普通 POS 回归和 Windows 手工冒烟全部通过。

## 当前实施结果

### ME-1.1（2026-09-23）

- 已新增 `V1.0.5__create_member_brand_benefit_ledger.sql`：品牌档位、QUANTITY/AMOUNT 主记录与流水、TARGET 主记录与进度流水；金额档位为 `decimal(12,2)` 配置值，未固化 660/2700 等示例金额。
- 已新增 UMS 内部 `MemberBrandBenefitLedgerCommandHandler` 及实现。数量/金额变动按请求号幂等，以行锁和条件更新拒绝负余额；TARGET 创建总会写 `INITIAL_PROGRESS` 流水；品牌等级仅在候选档位 `rank` 更高时更新。
- 已新增覆盖金额权益幂等/负余额、TARGET 初始进度和等级不降级的集成测试。
- 已通过：受控本机 `money_pos_test` 上的 `MemberBrandBenefitLedgerServiceIntegrationTest`（3 tests，0 failures/errors）与既有 `CheckoutIntegrationTest`（17 tests，0 failures/errors）；其中 Flyway 已将隔离库从 `1.0.4` 升级至 `1.0.5`。同时通过 `mvn -pl qk-money-app/money-app-biz -am compile -DskipTests -q`、`test-compile`、`mvn -q package -DskipTests`、`scripts/architecture-scan.sh --check-new`、`git diff --check`。
- 首次测试失败只是当前 shell 未注入测试变量；完整阅读接力说明后，已按文档规定从 `application-dev.yml` 提取既有本地开发凭据，并以固定 `money_pos_test` URL 受控执行。普通沙箱的 `Operation not permitted` 是本机 3306 网络限制，不是变量或数据库缺失。

ME-1.1 已具备开始 ME-1.2 的前置条件；下一切片仅实现 QUANTITY 延迟履约，不提前混入 AMOUNT 或 TARGET。
