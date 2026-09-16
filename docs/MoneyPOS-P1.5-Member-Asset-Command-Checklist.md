# MoneyPOS P1.5：会员资产结算与退款命令清单

## 目标与不变量

将 TRADE 对会员资产的直接写入收敛为 UMS 所拥有的场景命令，同时保持单体应用内现有结账事务：订单、库存、会员资产、支付流水任一步失败均回滚。此阶段不拆 Maven 模块、不改数据库或 Flyway，也不改变优惠券 FIFO、余额原子扣减和退款金额口径。

## 当前写侧盘点

| 场景 | 当前 TRADE 写入 | 目标 UMS 命令责任 |
| --- | --- | --- |
| 结算会员消费 | `consume` 消费额/会员券、余额原子扣减、到店时间 | 会员消费、会员券扣减、余额扣减、到店时间、对应日志 |
| 结算满减券 | 查询 FIFO 可用券、条件更新为 `USED`、写券流水 | 券选择/并发条件更新、券流水 |
| 全额退款 | 返还消费/会员券、恢复满减券、返还余额、写日志 | 会员资产退款、满减券恢复、余额退款及日志 |
| 部分退款 | 返还指定销售额和会员券，不返还满减券/余额 | 指定资产退款及日志 |

## 实施顺序

- [x] P1.5.1 写侧盘点与特征基线：确认 `PosAssetActionService` 仍直接依赖 `UmsMemberService`、`UmsMemberLogMapper`、`PosMemberCouponMapper`；`MemberAssetFacade` 仍直接恢复满减券。现有 `CheckoutIntegrationTest` 覆盖余额结算、余额不足全事务回滚、满减券核销/全额退款恢复、全额余额退款、部分退款库存与订单状态。
- [x] P1.5.2 已补齐缺失特征测试：新增“部分退款叠加余额支付不返还整单余额”及“满减券数量不足、在资产核销阶段失败时订单/库存/会员资产全部回滚”测试。真实并发条件更新的竞争窗口保留至 P1.5.6 并发复核。
- [x] P1.5.3 已完成结算命令设计：详见 `MoneyPOS-P1.5-Member-Settlement-Command-Design.md`。命令只表达会员、订单、金额和券规则信息；将 `NormalizedPaymentResult` 收敛为中立的余额支付净额，UMS 将拥有消费、券核销、余额、日志和到店时间写入。
- [x] P1.5.4 已完成退款命令设计：详见 `MoneyPOS-P1.5-Member-Refund-Command-Design.md`。统一命令用标志位和余额退款金额表达整单/部分退款差异，不携带订单、支付、会员或券实体。
- [x] P1.5.5 已迁移 `MemberAssetFacade`：新增 API 中立的 `MemberSettlementCommand` / `MemberRefundCommand` 及处理器；UMS 实现消费、满减券 FIFO 条件核销/恢复、余额和日志写入，TRADE 门面仅从既有结账/退款结果组装命令。已删除 `PosAssetActionService` 和 TRADE 内直接的 UMS Mapper/Entity 写入；结账与退款外层事务保持不变。
- [ ] P1.5.6 全量验证与并发复核：运行阶段 0 `CheckoutIntegrationTest`、全量 `mvn test`、`mvn package` 和架构门禁；复核失败回滚、FIFO、全额/部分退款、重复请求行为。

## 本轮结论

下一最小任务是 **P1.5.6：补真实并发满减券竞争回归，并完成 P1.5 全量验证与调用面复核**。
