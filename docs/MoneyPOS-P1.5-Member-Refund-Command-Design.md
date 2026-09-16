# MoneyPOS P1.5.4：会员退款命令设计

## 命令边界

TRADE 完成订单状态锁定、库存回补和退款金额计算后，向 UMS 发出一次会员退款命令。命令不携带 `OmsOrder`、`OmsOrderPay`、`UmsMember`、`PosMemberCoupon` 或任何 Mapper/实体；支付明细在 TRADE 内汇总为余额退款金额。

```text
MemberRefundCommand
  memberId                 必填：订单关联会员 ID
  orderNo                  必填：原订单号
  salesAmount              必填，允许 0：需回退的会员消费额
  memberCouponRefund       必填，允许 0：需返还的会员券金额
  increaseCancelTimes      必填：仅整单退款为 true
  restoreVouchers          必填：仅整单且原单使用满减券时为 true
  balanceRefundAmount      必填，允许 0：仅整单中原余额支付金额
```

中立接口为 `MemberRefundCommandHandler.handle(command)`，返回 `void`。它加入既有 `OmsOrderRefundServiceImpl` 外层事务；任一步失败均回滚订单状态、库存、会员资产与日志。

## 全额与部分退款规则

| 字段/行为 | 整单退款 | 部分退款 |
| --- | --- | --- |
| `salesAmount` | 原订单 `finalSalesAmount` | 退货数量 × 单品成交价 |
| `memberCouponRefund` | 原订单 `couponAmount` | 单品已分摊会员券 × 退货数量 |
| `increaseCancelTimes` | `true` | `false` |
| `restoreVouchers` | 原单实际使用满减券时 `true` | `false` |
| `balanceRefundAmount` | 原支付流水中 `BALANCE` 的金额合计 | `0` |

因此，部分退款不会一次性返还整单余额支付，也不会恢复整单满减券；这一规则已由 P1.5.2 特征测试保护。

## UMS 写侧责任

1. 会员不存在时保持现有退款降级行为：跳过会员资产回退，保证订单和库存退款流程可继续。
2. 原子回退消费额/会员券，必要时增加取消次数，并记录会员券退款日志。
3. `restoreVouchers=true` 时，按 `orderNo` 将已使用满减券恢复为 `UNUSED`、清空使用时间与订单号，记录券退款日志。
4. `balanceRefundAmount>0` 时原子返还余额并记录余额退款日志。
5. 所有日志维持现有 `orderNo` 关联与金额口径。

## 迁移顺序

P1.5.5 将同时实现结算与退款两个命令处理器，并让 `MemberAssetFacade` 只负责构造命令和调用中立接口。迁移完成后删除 TRADE 内 `PosAssetActionService` 及 `MemberAssetFacade` 中的 UMS/券 Mapper 写入。
