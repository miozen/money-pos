# MoneyPOS ME-1.6：会员权益 UX 设计与分阶段实施合同

> 状态：**Frozen implementation contract — ME-1.6B complete, ME-1.6C next**；冻结日期：2026-10-08；适用分支：`dev`。
>
> 本文是 ME-1.6 的唯一设计与后续实施合同。它基于 ME-1.5 已提交实现和 ME-1.6 审查结果，
> 只收敛操作体验与必要的配置模型；不改变 QUANTITY、AMOUNT、TARGET 已冻结的交易、库存、收入、
> 支付、退款、价格快照和权限边界。本文本身不代表 ME-1.5E 已完成：Windows Electron 冒烟证据仍是
> ME-1.5 的独立关闭条件。

## 1. 产品冻结规则

### 1.1 共同原则

- 后台定义规则，POS 只办理顾客当下的业务。
- 复用普通 POS 的扫码、选商品、清单、数量和混合收款交互；不得共享普通 `cartList`、`currentMember`、
  `paymentList` 或普通订单提交状态。
- 权益清单是各业务窗口的局部 UI 状态，不建立“万能权益交易”或统一落库模型。
- POS 不展示模型英文名、内部 ID、状态码、价格档编码、`rankValue`、`sortNo`、原始凭证号。
- 既有 RBAC 能力优先复用；前端隐藏不替代后端 `@PreAuthorize`。

### 1.2 商品寄存（QUANTITY）

POS 流程为：选会员 → 扫码/搜索商品 → 编辑局部清单与数量 → 收款 → 完成寄存；提货为：选会员 →
查看可提商品 → 勾选多项及数量 → 确认提货。

- 单次寄存支持多个商品；单次提货适配后端已存在的多权益行原子提货。
- 寄存购买确认收入、生成普通订单，但不扣实体库存；实际提货继续走 `MEMBER_PICKUP` 扣库存路径。
- 不配置固定套餐，不与 AMOUNT 同笔混合办理。

### 1.3 品牌权益包（AMOUNT）

POS 流程为：选会员 → 选品牌及权益包 → 收款 → 完成；使用为：选会员 → 选**一个**有效权益包 →
扫同品牌商品 → 按发放时冻结价格档计价 → 权益余额抵扣 → 必要时混合支付补差 → 完成。

- 同一使用交易只选一个权益包；同品牌多份有效权益可以并列展示，绝不自动合并余额。
- 确认前必须显示商品金额、权益抵扣、剩余余额、补差和支付分摊。
- 包购买、提货补差、整笔提货退款继续使用现有专用业务凭证；不得伪装为普通商品订单。
- 已发放权益继续使用其品牌、档位名称和价格档快照；后台改套餐不得追溯改写历史权益。

### 1.4 会员升级计划（TARGET）

POS 可由有 `memberBenefit:operate` 权限的店员建立计划：选择会员、品牌和可用目标，展示目标金额、
当前进度、目标价格档说明，确认后创建计划；该操作不是预付购买。后台可以管理计划。

- 正常销售仍使用普通 POS 购物车。仅当已选会员、购物车全部属于一个品牌、且该品牌存在进行中的计划时，
  Checkout 显示“计入升级计划”的显式选择；未选即普通销售，不自动累计。
- 选择计划时，Checkout 以既有 `/pos/target/settle` 进入 `MemberTargetBenefitService`；不得先走普通
  `/pos/settleAccounts` 再补写贡献，也不得复制订单、库存或支付逻辑。
- 混合品牌购物车不可计入单一计划：界面禁用选择并说明“请拆单或按普通销售结算”。不新增自动拆单或部分累计。
- 现有事实是 TARGET 专用结算复用普通 Checkout 的**当前会员品牌价格**；`targetPricingLevelCodeSnapshot`
  是计划目标/审计快照，不改变计划期间的成交价。本文不把目标价作为提前生效价。
- 初始进度、只升不降、达标人工确认、补差/豁免、退款后 `REVIEW_REQUIRED` 保持既有规则。补差、豁免、
  确认、退款复核只在店长/后台风险处理入口提供。

## 2. 页面与权限信息架构

### 2.1 POS「会员权益」全屏窗口

保留主 POS full-screen dialog 和手机号/姓名的 `MemberSmartSearch`，但改为三张业务入口卡，不再以技术
表格和 Tabs 作为首屏。

| 入口 | 正常店员操作 | 页面摘要 | 高风险操作 |
| --- | --- | --- | --- |
| 商品寄存 | 寄存、提货 | 可提商品、剩余件数 | 不在 POS 常规入口显示 |
| 品牌权益包 | 购买、使用余额 | 品牌、包名称、可用余额、价格档中文名 | 整笔退款移至后台处理 |
| 会员升级计划 | 建立计划、查看进度 | 目标、进度、剩余金额、状态 | 补差、豁免、确认、复核移至授权入口 |

窗口关闭、取消或操作失败时，销毁/重置本窗口局部会员、清单、支付和提交状态；普通 POS 购物车和会员选择
必须保持原值。现有 `views/ums/memberBenefit/MemberBenefitWorkspace.vue` 是迁移来源，不继续扩充为混合工作台。

### 2.2 后台菜单

| 菜单 | 职责 | 推荐权限 |
| --- | --- | --- |
| 权益规则配置 | AMOUNT 包、TARGET 目标、品牌价格档引用、启停与展示排序 | `memberBenefit:tier` |
| 会员权益管理 | 按会员查询寄存、余额、计划及状态 | `memberBenefit:operate` 或 `manage` |
| 权益业务处理 | AMOUNT 整笔退款、TARGET 补差/豁免/确认/退款复核 | `memberBenefit:manage` |
| 权益交易记录 | 购买、提货、抵扣、退款与审计详情 | `memberBenefit:operate` 或 `manage` |

后台详情可以显示业务单号和审计字段；普通 POS 不显示。`memberBenefit:operate/manage/tier` 三项能力保持，
不新增权限体系；实施时只细化既有权限的菜单与按钮可见性。

## 3. 组件、局部状态与接口设计

### 3.1 可复用边界

| 能力 | 复用方式 | 禁止共享 |
| --- | --- | --- |
| 会员搜索 | 直接复用 `MemberSmartSearch` 和 `/ums/member/pos-search` | `usePosStore.currentMember` 的写入 |
| 条码分发 | 复用 `useScanner` 的窗口内分发 | `scanAndAddToCart` |
| 商品搜索/清单 | 抽取或适配 `SmartGoodsSelector`、购物车行数量编辑和汇总 UI | 普通 `cartList`、试算状态 |
| 收款 UI | 复用支付方式/标签字典、金额分摊、找零与禁用提交交互 | `CheckoutModal` 的 store 与 `submitOrder` |
| 普通销售 | TARGET 仅复用现有 Checkout 订单服务 | AMOUNT/QUANTITY 写入普通结算 |

### 3.2 权益购物清单

各业务拥有独立 `BenefitCartState`（前端组合式状态，而非后端通用 DTO）：`items`、`member`、
`selectedBrand`、`selectedRight/package/plan`、`paymentDraft`、`submitting`。窗口关闭、成功、切换会员均清空。

- QUANTITY 寄存项为 `{goodsId, quantity}`，提交既有 `/pos/deferred-quantity/settle`。
- QUANTITY 提货将可提权益按“商品名称 + 剩余数量”分组，勾选后生成多个 `{rightId, quantity}`，提交既有
  `/pos/deferred-quantity/pickup`；前端先限制不超过剩余数量，后端仍是库存和并发最终裁决。
- AMOUNT 使用项为 `{goodsId, quantity}`，但先锁定一份 `amountRightId`；只显示与该权益品牌匹配的商品。
  预览接口必须按该权益冻结价格档返回商品金额、可抵扣额、补差和余额后金额，确认才调用既有 pickup 服务。
- TARGET 销售清单是普通 Checkout 购物车，不复制到权益购物车；建立计划不需要商品清单。

### 3.3 `BenefitPaymentEditor` 最小改造

保留该组件及其局部 payments payload，升级为共享的“支付交互壳”：支付方式/聚合标签、分摊、应收/已收、
未收、现金找零、校验错误、提交中禁用和关闭重置。它不读取 `usePosStore`。

对 QUANTITY 寄存与 TARGET 销售，由试算/结算前预览提供应收；对 AMOUNT 包和 TARGET 补差使用配置金额；
对 AMOUNT 提货使用新增预览返回的补差。提交请求携带一次生成的 `reqId`，按钮在请求期间锁定；网络失败或
服务端拒绝保留清单和支付草稿以便修正，取消/关闭才清空。

### 3.4 API 合同

继续直接复用：会员搜索、权益 overview/history、QUANTITY purchase/pickup、AMOUNT purchase/pickup/refund、
TARGET settle/supplement/waive/confirm。

| 缺口 | 最小新增接口 | 责任归属 |
| --- | --- | --- |
| QUANTITY 提货可读商品清单 | 在 overview 增加业务显示快照，或新增受保护的 `GET /ums/member-benefit/quantity-pickup-options` | UMS 读模型 |
| AMOUNT 提货金额预览 | `POST /pos/amount-package/pickup-preview`，输入 `memberId`、`amountRightId`、商品行，返回校验、逐行价、总额、抵扣、补差、余额后金额 | TRADE 的 `MemberAmountBenefitService` 读/试算边界 |
| TARGET 建立计划 | `POST /pos/target/plans`，输入会员、品牌、目标档位、初始进度（默认 0）、原因、`reqId` | UMS 账本命令边界，经 Entity-free contract |
| Checkout 计划候选 | `GET /ums/member-benefit/target-plan-options?memberId=&brandId=`，只返回可选 `IN_PROGRESS` 计划的业务摘要 | UMS 读模型 |

TARGET Checkout 的提交不新增第二个普通订单接口：`CheckoutModal` 增加可选 `targetPlanId` 的 UI 输入；有值时由 POS
调用已存在的 `/pos/target/settle` 并传原 `SettleAccountsDTO`，无值时仍调 `/pos/settleAccounts`。控制器和服务端
仍必须验证会员、计划状态和所有商品品牌。

## 4. 后台配置模型与历史兼容

现有 `ums_brand_benefit_tier` 同时承载等级、价格档、金额和排序，已发放 AMOUNT 权益和 TARGET 计划持有其
快照。不能仅为了前端标签改写或重解释这些历史行。

### 4.1 最小模型演进

若现有档位无法同时清晰配置 AMOUNT 包和 TARGET 目标，ME-1.6E 新增 UMS 自有的
`ums_brand_amount_package`，而不是把互斥字段继续堆入 tier：

`brand_id`、`package_code`、`package_name`、`purchase_amount`、`benefit_amount`、
`pricing_level_code`、`enabled`、`sort_no`、`remark`，以及可选的 `legacy_tier_code` 映射。

- `ums_brand_benefit_tier` 继续是品牌等级/TARGET 目标和既有历史快照的权威来源，保留 `configuredAmount`、
  `rankValue`、`pricingLevelCode` 的既有语义。
- AMOUNT 新包改为引用 package；发放时仍把包名、权益金额、品牌和价格档写入金额权益快照。
- 迁移必须先为现有可用 AMOUNT tier 生成一条一对一 legacy package；旧 route/旧 tier 请求保持可读、可审计，
  直到历史权益全部自然结束前不得删除旧快照字段或改写其值。
- TARGET 规则在本合同中最小为“品牌 + 可用目标档 + 目标金额 + 启停”；复杂商品范围、跨品牌累计或自动降级
  不在本阶段引入。商品品牌全匹配继续是现有累计规则。

这项迁移仅在 ME-1.6E 实施，且须先用现有真实数据和迁移测试确认不存在语义丢失；若现有 tier 已可承载经过
产品命名后的 package 配置，可不执行该表迁移，但必须记录验证证据。

## 5. 分阶段实施合同

### ME-1.6B：POS 三模型入口与会员权益摘要

- **范围/文件**：`views/ums/memberBenefit/` 拆成三项业务页或组件；`views/pos/index.vue`、`HeaderBar.vue`；
  `api/ums/memberBenefit.js`；仅必要的权益 read DTO/Controller。
- **复用**：`MemberSmartSearch`、现有 overview、POS full-screen dialog、现有权限。
- **新增**：业务可读摘要（品牌名、商品名、价格档中文名、状态文案）；如 overview 不足，补 UMS 只读字段。
- **不得改变**：任何写交易、库存、支付或退款路径；局部会员不绑定普通 POS。
- **验收/测试**：未选会员不能办理；手机号/姓名切换不污染普通购物车；三入口仅显示业务文字；多权益摘要正确；
  `operate/manage` 正反授权和前端按钮隐藏。
- **完成/依赖**：三个入口和摘要可导航且状态隔离后完成；为 1.6C 提供稳定局部状态容器。

### ME-1.6C：权益清单、扫码、多行提货与支付体验

- **范围/文件**：权益清单/扫码分发组件、`BenefitPaymentEditor.vue`、`useScanner.js` 的窗口分发、
  `MemberAmountBenefitService` 的 preview API、QUANTITY pickup UI/API 适配及对应测试。
- **复用**：`SmartGoodsSelector`、普通清单数量 UI、支付字典/标签、既有 QUANTITY/AMOUNT 写服务。
- **新增**：AMOUNT pickup preview；必要时 QUANTITY 提货展示读接口。无新统一交易 DTO。
- **不得改变**：QUANTITY 寄存不扣库存、提货才扣库存；AMOUNT 凭证/整笔退款/一个权益包规则；普通 POS 状态隔离。
- **验收/测试**：多商品寄存、多权益行原子提货；错误品牌、余额不足、库存不足、支付失败、关闭/取消、重复点击；
  补差、混合支付、余额足额支付和普通 Checkout 回归。
- **完成/依赖**：所有权益商品操作有清晰预览、错误可恢复、成功可防重；为 TARGET Checkout 复用支付交互。

### ME-1.6D：TARGET 建立计划与普通 Checkout 联动

- **范围/文件**：UMS target-plan 创建 read/write API 与 Entity-free contract 使用点；POS 权益计划页；
  `CheckoutModal.vue`、POS 结算分发；`MemberTargetBenefitService` 与 Controller 的契约测试。
- **复用**：既有 `MemberBrandBenefitLedgerService.createTargetPlan`、target overview、`/pos/target/settle`、
  `CheckoutOrchestrator`。
- **新增**：计划创建 route、计划候选查询、Checkout 的 `targetPlanId` UI 选择；不新建第二套交易服务。
- **不得改变**：未选择计划仍走普通 checkout；选择计划只走 target settle；混合品牌不得部分累计；当前会员价规则、
  人工确认和退款复核不变。
- **验收/测试**：创建计划幂等、同会员/品牌校验、计划状态校验、单品牌显式累计、未选不累计、混合品牌拒绝选择、
  全额/部分退款转复核、达标未确认/确认后只升不降。
- **完成/依赖**：计划可在 POS 建立并从正常结算显式计入，且普通销售没有回归。

### ME-1.6E：后台规则配置、管理页面及权限收敛

- **范围/文件**：`views/ums/memberBenefit/` 后台壳拆分、`memberBenefitTier/`、新增 amount package 页面/API、
  UMS Application/DTO/Mapper、Flyway、动态菜单/权限迁移和回归测试。
- **复用**：GMS `BrandSelectionQuery` / 价格档 query（仅 Entity-free contract）、既有 tier CRUD、RBAC 菜单。
- **新增**：仅在模型核验确有缺口时新增 amount package 表及迁移；TARGET 继续复用 tier，不新增泛化规则引擎。
- **不得改变**：已发放权益/计划的快照、旧 API 兼容、GMS 数据所有权、跨 Feature 禁止 Entity/Mapper 依赖。
- **验收/测试**：品牌/价格档合法性、包/目标启停只影响新办理、历史权益仍按旧快照使用、菜单/按钮权限、
  迁移后新旧数据读写、无权直接访问 API 被拒绝。
- **完成/依赖**：后台四域可用、数据迁移与历史兼容验证完成，进入最终验收。

### ME-1.6F：完整回归、Windows 双屏验收与文档收口

- **范围**：相关 Java/Vue 测试、架构扫描、文档、Windows Electron 人工验收；不引入新业务功能。
- **自动验证**：隔离 `money_pos_test` 的权益/checkout/refund 回归，`mvn -q test`、`mvn -q package -DskipTests`、
  `npm run build`（按开发文档的 Maven/Node 入口）、两项 architecture scan、`git diff --check`。
- **Windows 验收**：1280×800 POS、扫码焦点、普通购物车隔离、支付失败/重试、窗口关闭恢复、客显/双屏不串状态，
  以及三类权益的成功与拒绝路径。
- **完成条件**：所有验证有实际结果；计划与 handoff 更新；提交、推送并确认 `dev...origin/dev` 无 ahead/behind。

## 6. 不可变架构和数据边界

- 跨 Feature 仅使用 `money-app-api` 的 Entity-free contract，禁止 UMS/TRADE/GMS 相互依赖 Entity、Mapper 或实现类。
- `DeferredQuantityPickupService` 继续拥有 QUANTITY 提货；`MemberAmountBenefitService` 继续拥有 AMOUNT 凭证、
  补差、退款与事务；`MemberTargetBenefitService` 继续拥有 TARGET 贡献和风险规则。
- 新 preview 是不落库的服务读/试算，最终提交必须重新校验价格、余额、库存、品牌和权限；前端金额仅作提示。
- 所有写请求保留/新增 `reqId` 幂等；重复提交不得创建第二笔订单、凭证、权益扣减或进度。

## 7. 已知风险与实施前置

1. 当前 `MemberBenefitWorkspace` 暴露内部 ID、技术状态和高风险 TARGET 动作，必须在 1.6B 按权限和业务文案收敛。
2. AMOUNT 提货当前没有提交前正式试算，1.6C 的 preview 是支付清晰度与防误收的必要前置。
3. 当前 TARGET 计划创建没有 POS/REST 产品入口，1.6D 必须补齐，但仅可调用既有 UMS 账本命令边界。
4. “目标价格档”只能作为目标快照/升级后价格说明，除非另行冻结改变当前成交价的规则；本合同不改变现有当前会员价结算。
5. ME-1.5E 的 Windows 冒烟尚未闭环。ME-1.6B 实施得到确认后仍须避免将其替代或误记为 ME-1.5 的验收结果。
