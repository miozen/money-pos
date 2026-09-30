# MoneyPOS ME-1.5：会员权益操作化实施合同

> 状态：**Frozen contract — implementation in progress (ME-1.5B)**；冻结日期：2026-09-29；适用分支：`dev`。
>
> 本文是 ME-1.5 操作化部分的唯一实施与验收合同。它保留既有 ME-1.5 FIN/HOME 投影、读 API 和后台页面本地基础成果，
> 并以本次冻结的 POS 前台化、后台配置化决定重新定义后续 A–E 的唯一执行顺序。本文不改变 ME-1.1～ME-1.4 的业务模型。

## 1. Background

ME-1.1～ME-1.4 已建立品牌权益账本、QUANTITY 延迟履约、AMOUNT 权益包/混合补差/整笔退款和 TARGET 结算/进度规则。
其中 AMOUNT 首购和补差是非商品业务，使用 TRADE 业务凭证及支付明细，绝不写入 `oms_order` 或 `oms_order_pay`。

当前 ME-1.5 已在本地完成非商品凭证 FIN/HOME 投影、权益读 API 与一个后台权益页面基础；但该页面仍要求输入
`memberId`，没有 POS 现场入口，档位缺少管理能力，且静态路由绕过了后台动态菜单。因此本合同只负责把已冻结的能力
操作化，不重新设计权益模型。

## 2. Current State

| 领域 | 已有事实 | 操作化结论 |
| --- | --- | --- |
| POS 主界面 | `money-pos-web/src/views/pos/index.vue` 以 `HeaderBar` 和集中 `dialogs` 承载现场操作 | 可增加一个本地主窗口 dialog，不新增 BrowserWindow |
| POS 会员 | `usePosStore.currentMember`、`MemberBindModal`、`MemberSmartSearch` 已使用 `/ums/member/pos-search` | 当前会员只作默认值；权益工作台必须有隔离的 `selectedMember` |
| POS 购物篮 | `usePosStore.cartList`、试算和 `CheckoutModal` 是普通订单专属状态 | 权益提货项及权益支付不得写入这些状态 |
| Electron | `money-pos-web/main.cjs` 仅有主 POS、客显和独立登录的后台窗口 | 不复用独立后台窗口；权益办理为主窗口 full-screen dialog |
| 权益页面 | `money-pos-web/src/views/ums/memberBenefit/index.vue` 已覆盖概览、部分购买/提货/TARGET/历史 | 抽取为 Workspace，不复制业务页面 |
| 权益 API | `money-pos-web/src/api/ums/memberBenefit.js` 已指向专用 POS/UMS/TRADE API | 继续复用；仅补当前后端能力的 UI 覆盖和必要读/权限接口 |
| TRADE 写服务 | `MemberAmountBenefitService`、`DeferredQuantityPickupService`、`MemberTargetBenefitService` 已存在 | 不重写、不穿透 Mapper，不改变事务归属 |
| 档位 | `ums_brand_benefit_tier` 有 Entity、Mapper、账本和只读概览 | 需补 UMS 内部 CRUD Application Service、API、后台页和权限 |
| 品牌/价格 | GMS 品牌、`BrandSelectionQuery`、品牌定价策略、商品价格矩阵已存在 | 只复用，不建立第二套品牌或价格体系 |
| 权限 | `sys_permission`、角色-权限关系、动态路由、`@rbac`、`userStore.hasPermission()` 已存在 | 用三个权益能力权限接入现有机制 |

## 3. Goals

1. 在 POS 顶部提供“权益办理”现场入口。
2. 使操作员以手机号或姓名选择会员，永不要求输入 `memberId`。
3. 使权益办理与 POS 当前会员、普通购物篮、普通订单和普通支付状态完全隔离。
4. 让 POS 与后台复用一个 `MemberBenefitWorkspace` 及同一组后端 Application Service/API。
5. 将权益日常办理前台化，将权益查询、退款/异常和档位配置后台化。
6. 以既有 GMS 品牌、品牌价格档和 RBAC 完成配置与授权。

## 4. Non-Goals

- 不重构 ME-1.3/ME-1.4 已冻结的权益、凭证、库存、退款或 TARGET 模型。
- 不改变 MariaDB 类型、数据库基础设施或普通 POS 结算公式。
- 不将 AMOUNT 权益包、权益补差或权益提货伪装为普通商品订单。
- 不创建第二套会员搜索、品牌、价格档、支付账本或前后台业务服务。
- 不在本阶段创建通用“权益调整”余额 CRUD。
- 不开展与权益操作化无关的大型前端、Electron、RBAC 或 Feature 重构。

## 5. Frozen Product Decisions

1. POS `HeaderBar` 与销售单、交接班、会员充值同一区域增加“权益办理”。
2. 权益办理使用 POS 主窗口内 **full-screen `el-dialog`**。理由是权益页面包含多个表格、办理表单、支付与流水；
   大尺寸普通 dialog 在 1280×800 POS 窗口不足以承载完整工作台。现有 `dialogs` 机制会暂停主界面快捷键/扫码处理。
3. 不创建新的 Electron `BrowserWindow`；现有后台窗口有独立 session/登录清理生命周期，只适合后台管理。
4. POS `currentMember` 仅为 Workspace 的初始默认值；Workspace 永远允许手机号/姓名重搜并切换会员。
5. Workspace 的 `selectedMember` 是局部状态。它不得写 `usePosStore.currentMember`、`cartList`、`paymentList`，也不得调用
   `bindMember`、触发普通商品试算或改变普通订单价格。
6. 会员搜索复用 `GET /ums/member/pos-search?keyword=` 与 `MemberSmartSearch`（或其最小适配），不暴露 `memberId`。
7. QUANTITY/AMOUNT 提货支持商品搜索与扫码；结果只写 Workspace 的 `pickupItems`。
8. 权益支付复用现有混合支付交互和支付字典，但不得复用普通 Checkout 的购物篮、支付状态或 `/pos/settleAccounts` 提交。
9. 权益权限暂时冻结为 `memberBenefit:operate`、`memberBenefit:manage`、`memberBenefit:tier` 三项；不预设任何角色名称。
10. 通用权益调整标记为 Deferred，须先完成业务规则冻结后才可增加受控 Application Service。

## 6. Architecture Guardrails

- 跨 Feature 协作只使用 `money-app-api` 的 Entity-free contract；UMS 档位管理使用 `BrandSelectionQuery`，不得直接访问
  GMS Entity 或 Mapper。
- `MemberAmountBenefitService` 继续拥有 AMOUNT 凭证、支付、补差、退款的业务事务；`DeferredQuantityPickupService`
  继续拥有 QUANTITY 提货；`MemberTargetBenefitService` 继续拥有 TARGET。
- 只有 TARGET 正常商品销售可沿既有 `CheckoutOrchestrator` 走普通 Checkout；这不为其他权益业务建立例外。
- 权益 UI 只能调用 Controller/API，不可直接访问持久化 Entity/Mapper 或账本命令实现。
- 现有公开路由、响应字段、迁移与公式除本文明确项外不变。

## 7. UX Flow

```text
POS 顶部「权益办理」
        ↓
打开 full-screen 权益工作台
        ↓
POS 当前会员存在？
 ├─ 是 → selectedMember 初始化为 currentMember
 └─ 否 → 直接显示会员手机号/姓名搜索
        ↓
工作台中始终可重新搜索/切换 selectedMember
        ↓
权益概览 / 购买权益 / 权益提货 / TARGET / 权益流水
        ↓
关闭工作台：POS currentMember、cartList、普通订单试算保持原值
```

Workspace 顶部显示姓名、手机号、会员品牌等级摘要和“切换会员”。品牌显示名称而非裸 ID；内部调用仍传递
`selectedMember.id`。

## 8. POS / Backend Responsibility Boundary

| POS 工作台 | 后台会员权益 | 后端既有边界 |
| --- | --- | --- |
| 查找/切换会员、概览、日常购买、提货、日常 TARGET、流水 | 查询、历史、退款、异常复核、高风险 TARGET 确认 | 继续由既有专用 Application Service 负责事务、幂等、审计、库存和凭证 |
| 本地 `pickupItems`、本地支付分摊 | 后台复用同一 Workspace 或其业务组件 | 不允许 UI 直接操作账本 Mapper/Entity |
| 不触碰 `cartList` | 档位配置不进入 POS | AMOUNT 继续写非商品凭证，TARGET 普通销售仍按既有 Checkout |

## 9. Component Reuse Map

| 目标 | 复用来源 | 最小适配 |
| --- | --- | --- |
| POS 入口 | `views/pos/components/HeaderBar.vue` 的 action 发射 | 新增 `benefit` action |
| 弹窗状态 | `views/pos/index.vue` 的 `dialogs`、Esc、快捷键隔离 | 新增 `dialogs.memberBenefit` |
| 默认会员 | `views/pos/hooks/usePosStore.js` 的 `currentMember` | 仅在 open 时浅拷贝给 Workspace prop |
| 会员搜索 | `components/common/MemberSmartSearch.vue`、`/ums/member/pos-search` | 只监听 `select` 写 Workspace 局部状态；不调用 `bindMember` |
| 现有权益功能 | `views/ums/memberBenefit/index.vue`、`api/ums/memberBenefit.js` | 拆为独立 Workspace 与后台壳，删除操作员 `memberId` 输入 |
| 商品搜索 | 既有 POS 商品查询/选择组件与 `/pos/goods` | 抽出“查询商品”结果，不调用 `scanAndAddToCart` |
| 扫码硬件 | `views/pos/hooks/useScanner.js` | 改为主窗口单一条码分发：普通 POS → cart；Workspace 打开 → pickupItems |
| 混合支付 | `views/pos/components/CheckoutModal.vue` 的支付字典、分摊和渠道交互 | 抽取局部 `BenefitPaymentEditor`，不使用 `usePosStore.paymentList` 或普通提交 |
| 品牌选择 | GMS `BrandSelectionQuery` 与 `/gms/brand/select` | 档位 UI 使用现有品牌数据 |
| 价格档选择 | `/gms/brand/config?brandId`、商品价格矩阵、`memberType` | 仅列出该品牌允许的 level code |

## 10. API Reuse / API Gap

### Direct reuse

- `GET /ums/member/pos-search`：会员手机号/姓名搜索。
- `GET /ums/member-benefit/overview`、`GET /ums/member-benefit/target-logs`、
  `GET /member-benefit/trade-history`：概览和历史。
- `POST /pos/deferred-quantity/settle`、`/pickup`：QUANTITY 购买和提货。
- `POST /pos/amount-package/purchase`、`/pickup`、`/pickup-refund`：AMOUNT 办理、补差、退款。
- `POST /pos/target/settle`、`/supplement`、`/waive`、`/confirm`：TARGET 已冻结行为。

### Minimal gaps

1. 当前 Workspace 页面尚未将 QUANTITY 购买、AMOUNT 提货退款、TARGET settle 接入 UI。
2. 当前页面以商品 ID 输入提货；需复用商品检索/扫码后生成局部 `pickupItems`。
3. 当前页面本地支付只覆盖 CASH/BALANCE；支付编辑器需沿用 POS 支付方式、聚合渠道标签及精确金额校验。
4. 档位缺少 CRUD API；需新增 UMS 自有 DTO、Application Service、Controller 和前端 API。
5. 读 API 和写 API 的权限目前分别依赖 `umsMember:list`、`pos:cashier`；需迁移到本合同的三项权益能力权限。
6. `UmsMemberPosController` 的 `/pos-search` 目前没有方法级 `@PreAuthorize`，权益入口复用前必须置于既有 POS/权益能力授权边界内。

## 11. Permission Design

| 权限 | API/UI 范围 |
| --- | --- |
| `memberBenefit:operate` | 概览、历史、QUANTITY/AMOUNT 购买与提货、AMOUNT 补差、TARGET 日常办理、POS 权益入口 |
| `memberBenefit:manage` | AMOUNT 提货退款、异常处理、TARGET 达标人工确认、未来冻结后的受控调整 |
| `memberBenefit:tier` | 档位列表、新增、编辑、启停、排序及后台档位菜单 |

- 权限资源通过既有权限管理添加 MENU/BUTTON，并用既有角色授权页面分配；不在合同中预设收银员、店长、管理员等角色。
- 后端采用既有 `@rbac.hasPermission(...)`；前端仅用 `userStore.hasPermission()` 控制入口/按钮可见性，后端必须保持最终强制校验。
- 后台“会员权益”动态菜单需允许 `operate` 或 `manage`；“权益档位管理”菜单需 `tier`。
- 当前静态 `/ums/member-benefit` 路由在 ME-1.5C 取消，改为权限树返回的动态菜单；POS dialog 不依赖后台路由。

## 12. Tier Management Design

`ums_brand_benefit_tier` 的既有字段为：`brandId`、`tierCode`、`tierName`、`configuredAmount`、
`pricingLevelCode`、`rankValue`、`enabled`、`sortNo`、`remark`。其既有唯一约束为 `(brand_id,tier_code)` 和
`(brand_id,rank_value)`。

- CRUD 仅管理这些既有字段；不得新增“权益品牌”、QUANTITY 套餐数量或其他未冻结字段。
- QUANTITY 权益按商品订单明细建立，不把它伪造成档位配置产品。
- `configuredAmount`、价格档和 rank 继续服务于已冻结的 AMOUNT/TARGET 模型。
- 保存时：验证 GMS 品牌存在；验证价格档属于该品牌定价策略允许项；保持数据库唯一约束，并在 UI 提示同品牌商品
  必须配置该价格档，否则 AMOUNT 提货会被既有后端拒绝。
- 启停只影响后续办理可选择的档位，不追溯篡改已发放权益的冻结档位快照。

## 13. Data / Transaction Boundary

- POS/后台仅组织请求；现有 Application Service 继续以 `reqId` 承担幂等。
- AMOUNT 包购买：`trade_business_receipt/payment` + UMS 金额权益账本；不写普通订单。
- AMOUNT 提货：按冻结价格档计算，权益抵扣不确认收入，补差才写非商品凭证和支付；库存命令与账本变更保持现有事务。
- QUANTITY：购买不扣实体库存，提货才扣库存；继续使用既有提货命令。
- AMOUNT 提货退款：仅既有整笔退款，原路冲回补差、恢复权益并回库。
- TARGET：普通销售仅在已冻结的 target settle 路径走 Checkout；补差/豁免继续为独立业务凭证。
- Workspace 本地 `selectedMember`、`pickupItems`、支付编辑状态绝不进入全局 POS Store。

## 14. Implementation Phases

### ME-1.5A — Workspace 基础与会员选择

抽取 Workspace，新增 POS 顶部入口与 full-screen dialog；当前会员作为默认值；复用 `MemberSmartSearch`；工作台可切换会员；
删除操作员 memberId 输入；验证状态隔离。

### ME-1.5B — 权益办理操作化

复用现有 API 完成 QUANTITY、AMOUNT、TARGET 已有后端能力的 UI 覆盖；接入 AMOUNT 提货退款和 TARGET settle；
将商品 ID 输入替换为工作台独立商品搜索/扫码与 pickupItems；抽取独立混合支付编辑器。

### ME-1.5C — 权限与后台入口

增加三个权益能力权限、对应后端授权和 UI 显示控制；新增后台动态“会员权益”菜单；移除静态路由绕过。

### ME-1.5D — 权益档位管理

新增 UMS 档位 CRUD、GMS 品牌与价格档选择、后台动态菜单、启停/排序和数据合法性校验。

### ME-1.5E — 最终验收与收口

执行自动化、架构门禁、打包、Windows POS 冒烟、文档收口、提交、推送及分支同步验证。

## 15. Expected File Scope

| 阶段 | 预计涉及文件/目录 |
| --- | --- |
| A | `money-pos-web/src/views/pos/index.vue`、`components/HeaderBar.vue`、`views/ums/memberBenefit/`、`components/common/MemberSmartSearch.vue`（仅必要适配） |
| B | `views/ums/memberBenefit/`、`api/ums/memberBenefit.js`、`views/pos/hooks/useScanner.js`、现有商品搜索选择组件、`CheckoutModal.vue` 的可复用支付子组件 |
| C | `money-app-system` 权限/菜单集成、权益 REST Controller、前端路由/后台权益壳页、权限测试 |
| D | `money-app-api` DTO/contract（仅必要时）、UMS Application/REST/Mapper 使用层、`views/ums/memberBenefitTier/`、GMS Entity-free brand/price query 使用点、Flyway 权限菜单数据（仅在冻结实施时） |
| E | 相关 Java/Vue 测试、`docs/MoneyPOS-ME-1-Member-Entitlement-Implementation-Checklist.md`、`docs/MoneyPOS-Repository-Handoff.md` |

## 16. Phase Acceptance Conditions

| 阶段 | 必须满足 |
| --- | --- |
| A | POS 有入口；无 memberId 输入；A 可默认带入、可切 B；关闭后 `currentMember`、`cartList`、试算金额不变 |
| B | 所有已冻结 QUANTITY/AMOUNT/TARGET API 有正确 UI 路径；扫码/搜索不入普通购物篮；支付不走 `/pos/settleAccounts`；退款仅整笔 |
| C | 未授权者看不到/不能调用；后台菜单动态注册；静态路由绕过被移除；三项权限覆盖冻结范围 |
| D | 品牌/价格档只来自既有 GMS；CRUD/启停/排序可用；无新未冻结业务字段；现有权益快照不被追溯改写 |
| E | 自动与手工验收通过，文档、提交、推送、`dev...origin/dev=0/0` 均有事实证据 |

## 17. Automated Test Plan

- 每阶段：`npm run build`、相关前端组件/交互测试（若项目测试基础支持）。
- A：验证 Workspace 初始会员和切换会员不调用 `bindMember`、不改 `cartList`、不触发 POS 试算。
- B：在 `money_pos_test` 覆盖现有 QUANTITY、AMOUNT、TARGET、退款、幂等和商品/品牌/价格档拒绝路径；保留普通 Checkout 回归。
- C：Controller 路由权限契约测试，包含 `operate/manage/tier` 的正反授权。
- D：档位 CRUD、同品牌 code/rank 唯一、品牌存在性、允许价格档、停用不可新购、已发放权益快照不变。
- E：隔离库 `mvn -q test`、`mvn -q package -DskipTests`、`bash scripts/test-architecture-scan.sh`、
  `bash scripts/architecture-scan.sh --check-new` 和 `git diff --check`。

## 18. Manual Test Plan

- 空购物篮/未选会员：权益 dialog 打开后只能先搜索会员。
- 空购物篮/已选会员：默认显示当前会员，且可切换为另一会员。
- 非空购物篮/已选 A：打开权益、切换 B、给 B 办理或查看、关闭后，A、`cartList`、商品单价、总额、试算结果完全不变。
- 检查 AMOUNT 首购、混合补差、余额足额提货、QUANTITY 提货、TARGET 补差/豁免/确认和历史呈现。
- 扫商品条码时：打开 Workspace 加入 pickupItems；关闭 Workspace 加入普通购物篮。
- 检查无权限按钮隐藏与后端拒绝；检查后台菜单和档位配置。

## 19. Windows POS Smoke Test

在 Windows Electron 打包/运行环境中验证：

1. POS 顶部“权益办理”可见性符合权限。
2. full-screen dialog 在 1280×800 下可完整操作、Esc 关闭后焦点回到主 POS。
3. 扫码枪/扫码器在 dialog 内外的条码分发正确且不串入购物篮。
4. 非空购物篮状态隔离场景完整通过。
5. 权益支付、打印/提示、Windows 窗口关闭/恢复不影响 POS 主窗口和客显。

## 20. Regression Risks

- 将 `MemberBindModal` 直接复用会误绑定普通购物篮会员。
- 将 `scanAndAddToCart` 直接复用会污染 `cartList`。
- 将 `CheckoutModal` 直接嵌入会污染 `paymentList`、普通订单和试算状态。
- 放宽到普通 Checkout 会将非商品权益错误写入 `oms_order`。
- 静态路由保留会绕过后台动态菜单授权。
- 将停用档位应用到既有权益，或直接编辑账本余额，会破坏冻结快照与审计。

## 21. Rollback / Compatibility Considerations

- A/B 的 UI 添加在独立 dialog/Workspace 内；撤销入口不影响普通 POS 购物篮和 Checkout。
- C 的权限迁移必须在同一版本新增权限资源、角色授权策略与后端保护，避免已有使用者突然失去所有权益读写能力。
- D 的档位新建/停用只影响后续选择；历史权益保留 tier/价格快照。
- 数据迁移只在实施阶段新增，不在本冻结阶段执行；必须可由 Flyway 从现有数据库升级。

## 22. Deferred Items

通用权益调整为 **Deferred / Requires Business Rule Freeze**。在确定调整类型、正负规则、原因、操作人、审计、审批、
对历史流水影响与撤销语义之前，不新增 API，不暴露账本命令、Mapper 或 Entity CRUD。

## 23. Definition of Done

ME-1.5 仅在以下全部成立时完成：

1. POS 权益办理入口、会员局部状态隔离、商品扫码隔离、已冻结权益操作和后台档位管理均完成。
2. 非商品凭证/普通订单边界、品牌/价格档/Feature 边界与既有事务/幂等规则未被破坏。
3. 三项权限、动态菜单和后端强制授权完成。
4. 本文第 17～19 节的自动化与 Windows 冒烟均有真实通过证据。
5. 当前计划与 Repository Handoff 按事实更新，工作区检查通过，完成提交、推送，且 `dev...origin/dev=0/0`。

在此之前，**ME-1.5 operationalization is not complete.**
