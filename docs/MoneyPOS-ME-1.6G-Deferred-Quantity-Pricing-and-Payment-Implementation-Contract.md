# MoneyPOS ME-1.6G：商品寄存试算与局部混合收银实施合同

> 状态：**Frozen implementation contract — approved to implement**；冻结日期：2026-10-09；适用分支：`dev`。
>
> 本合同是对 ME-1.6 QUANTITY 寄存体验的窄补充。它只补齐提交前价格可见性、整单优惠和局部混合收银交互；
> 不改变已冻结的寄存收入确认、订单、退款、权限或“提货才扣库存”规则。

## 1. 已确认问题与目标

当前 `QuantityBenefitPanel` 直接提交 `/pos/deferred-quantity/settle`，服务端会重新计价，但前端没有显示
应收，也未传入 `manualDiscountAmount`；`BenefitPaymentEditor` 也未以应收约束寄存支付。因此店员无法在确认前核对
会员价、整单优惠、支付分摊与找零。

本阶段目标是让寄存拥有与普通 Checkout 等价的局部收银体验，并保持它与普通 POS 状态完全隔离。

## 2. 冻结业务规则

- 寄存仍创建普通订单并确认收入；只是不扣实体库存，提货继续是唯一库存扣减点。
- 寄存价格继续使用当前会员品牌价格、既有满减券/会员券规则和服务端最终重算；前端试算仅供展示。
- 新增的整单优惠复用 `SettleAccountsDTO.manualDiscountAmount`、现有风控上限、订单审计与报表字段。
- 商品、数量、会员、券或整单优惠改变后，旧试算和支付草稿不得继续作为可提交金额；必须按最新应收重置为聚合扫码全额。
- 默认支付为聚合扫码全额。修改现金、会员余额或其他支付后，剩余应收自动回填聚合扫码；会员余额不能超可用余额；
  非现金支付不得超应收；现金可超收并显示找零。
- 关闭、取消、切换权益会员或提交成功时，只清空本窗口局部寄存清单、试算和支付草稿；不得读写普通
  `usePosStore.cartList/currentMember/paymentList` 或普通订单提交状态。

## 3. 后端合同

新增 `POST /pos/deferred-quantity/trial`，权限为 `memberBenefit:operate`，请求复用 `SettleTrialReqDTO`，结果复用
`PricingResult`。它必须调用既有 `PosPricingFacade.priceDeferredQuantity()`：价格、会员和优惠规则与最终寄存结算一致，
但不检查实体库存。最终 `/pos/deferred-quantity/settle` 继续在事务内重算、校验支付、写订单和发放数量权益。

不得复用公开 `/pos/trial` 替代本接口，因为该接口会进行普通销售库存校验，违背“寄存不扣库存”的冻结规则。

## 4. 前端边界

- `QuantityBenefitPanel` 持有 `purchaseItems`、`pricingPreview`、`manualDiscount`、局部券选择与 `payments`。
- `BenefitPaymentEditor` 升级为无 store 依赖的支付交互壳：显示应收/已收/未收/找零，默认聚合扫码，自动补差，提交时暴露
  合法的局部 payload；AMOUNT 与其他调用方不得被迫共享寄存状态。
- 寄存确认弹窗展示价格摘要和整单优惠，提交按钮仅在最新试算、支付合法且未提交时可用。
- 请求仍只提交现有 `SettleAccountsDTO` 字段至既有寄存结算路由，不新增统一权益订单模型。

## 5. 验收与回归

- 多商品寄存显示服务端试算的会员价、优惠与最终应收；零库存商品可试算和寄存。
- 整单优惠传至最终订单；超限优惠由服务端拒绝。
- 默认聚合扫码；现金、余额、聚合扫码的自动补差、余额上限、现金找零与未收禁止提交均正确。
- 修改商品/数量/优惠后支付回到最新应收；网络或业务失败保留局部清单以便修正。
- 最终寄存再次试算并拒绝过期/非法支付，且不扣实体库存；多权益行提货回归不变。
- `memberBenefit:operate` 正反授权、普通 POS store 隔离、架构扫描、完整 Maven/前端构建回归通过。

## 6. 非目标

- 不改变 AMOUNT/TARGET 流程，不修改普通 Checkout 的路由、状态所有权或库存策略。
- 不引入自动拆单、部分库存预留、固定 QUANTITY 套餐或新的支付/订单实体。
- Windows EXE 冒烟仍是 ME-1.6F 的独立关闭条件。
