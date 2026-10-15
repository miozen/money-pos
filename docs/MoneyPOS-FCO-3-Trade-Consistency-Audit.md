# MoneyPOS FCO-3：TRADE 一致性审查记录

> 状态：**只读审查完成，未实施优化**；审查日期：2026-10-15；适用分支：`dev`。

## 现状地图

| 子域 | 所有者 | 调用与一致性边界 |
| --- | --- | --- |
| 普通/延迟结算 | `CheckoutOrchestrator`、`Checkout*Service` | TRADE 总事务：请求号幂等、校验/计价、建订单、GMS 库存命令、UMS 资产命令、支付、审计日志。 |
| 退款 | `OmsOrderRefundServiceImpl`、`RefundStateGuard` | 退款请求号由 `oms_refund_idempotent` 防重放；在同一事务内处理状态、库存和会员资产逆操作。 |
| 权益提货/TARGET | `MemberAmountBenefitService`、`DeferredQuantityPickupService`、`MemberTargetBenefitService` | 经 Entity-free UMS/GMS 契约协作；保留冻结的权益及 TARGET 规则。 |
| 订单、报表、硬件 | `OmsOrderService`、FIN/HOME 读查询、`PosPrinterService` | 订单路由用 `oms:order:*`，收银/权益路由有相应权限；打印为后置物理副作用。 |

主要证据：`CheckoutOrchestrator`、`CheckoutOrderService`、`OmsOrderRefundServiceImpl`、`RefundStateGuard`、
`PosController`、`OmsOrderController` 及 checkout/refund/权益集成测试。TRADE 对 GMS/UMS 的写入均经 API 命令/快照，
不直接持有对方 Entity 或 Mapper。

## 已确认约束

1. TRADE 保持普通结算、退款与跨 Feature 总事务所有者；不得迁移库存、会员资产或金额公式。
2. 普通结算以 `requestNo` 幂等，退款使用独立退款幂等记录；打印失败不得反转已成功交易。
3. 权益提货、金额权益、TARGET 的既有事务、确认和退款审计规则已冻结。

## 已确认问题

| 编号 | 影响 | 证据 | 风险 |
| --- | --- | --- | --- |
| TRADE-1 | 中 | `OmsOrderController.GET /oms-order/hardware/print` 直接调用打印机，却没有 `@PreAuthorize`；同控制器其余订单读取、退款、结算后打印和钱箱入口均受权限保护。 | 任意已认证用户可触发真实打印硬件副作用，和既有最小权限模型不一致。 |

## 最小优化候选

**TRADE-OPT-1：订单重打权限对齐。** 为重打路由复用既有 `oms:order:list`（订单可读权限），并增加路由权限契约测试；
不改路径、GET 方法、打印内容、钱箱行为、订单查询或角色授权。该候选不涉及结算、退款、金额、库存或会员规则。
