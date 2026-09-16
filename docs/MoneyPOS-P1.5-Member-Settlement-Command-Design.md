# MoneyPOS P1.5.3：会员结算命令设计

## 命令边界

TRADE 在订单、库存和支付归一化完成后，只向 UMS 发出一次会员结算命令。命令不携带 `UmsMember`、`PosMemberCoupon`、Mapper，也不携带 TRADE 内部的 `NormalizedPaymentResult` 或 `PricingResult`。

建议 API 契约：

```text
MemberSettlementCommand
  memberId                 必填：已核验会员 ID
  orderNo                  必填：订单幂等/流水关联号
  finalPayAmount           必填：订单最终应收金额
  memberCouponDeduct       必填，允许 0：应扣会员券金额
  voucherRuleId            可空：满减券规则 ID
  voucherCount             可空/0：要核销的满减券张数
  balancePaymentAmount     必填，允许 0：余额支付净额
```

中立接口为 `MemberSettlementCommandHandler.handle(command)`，返回 `void`；任何业务失败均抛出当前既有业务异常，加入外层 `CheckoutOrchestrator` 事务并回滚。

## 字段来源与责任

| 命令字段 | TRADE 来源 | UMS 责任 |
| --- | --- | --- |
| `memberId` | `MemberCheckoutSnapshot` | 确认会员存在 |
| `orderNo` | 已落库订单 | 关联资产和券流水 |
| `finalPayAmount` | `PricingResult.finalPayAmount` | 记录消费额/会员券统计 |
| `memberCouponDeduct` | `PricingResult.actualCouponDeduct` | 原子扣会员券、记录日志 |
| `voucherRuleId/count` | 结算请求 | FIFO 选择、`UNUSED → USED` 条件更新、券流水 |
| `balancePaymentAmount` | 已归一化支付项中 `BALANCE` 的净额汇总 | 不得大于最终应收；原子扣余额、记录日志 |

UMS 同时负责刷新 `lastVisitTime`。若命令失败，不能留下订单成功、库存已扣或任意资产半更新。

## 保留行为

- 满减券按领取时间、ID 升序选择；单次最多 500 张。
- 条件更新影响行数不足时失败并触发外层事务回滚。
- 余额支付意图存在但净额为 0 时失败；余额金额不得超过最终应收。
- 会员券、满减券、余额各自保留现有日志口径和 `orderNo` 关联。
- 此命令不处理退款；退款命令留给 P1.5.4。

## 下一步

P1.5.4 先以相同原则设计退款命令；两个命令同时准备完成后，P1.5.5 才一次性迁移 `MemberAssetFacade`，避免结算与退款在中途使用不同资产边界。
