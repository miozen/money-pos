# 阶段 5.1：UMS↔TRADE 循环依赖盘点与破环设计

## 结论

UMS 与 TRADE 当前不能物理拆为相互独立的 Maven 模块。原因不只是两方各有服务 import：TRADE 在收银、退款和 POS 查询中直接读取/写入 UMS Entity、Mapper 和 `IService<Entity>`，而 UMS 的会员资产导出反向调用 TRADE 的优惠券统计服务。

本文件只定义破环方向，不修改代码或 POM：未来创建一个不依赖 GMS、UMS、TRADE 实现的中立契约模块（暂称 `money-app-contract`）。两方都只依赖该契约模块；Spring Boot 组合层注入各 Feature 的实现。禁止让 `money-app-ums` 依赖 `money-app-trade`，再让 `money-app-trade` 反向依赖 UMS。

```text
money-app-ums   ─┐                         ┌─ implements coupon-count query
                 ├─> money-app-contract <─┤
money-app-trade ─┘                         └─ implements member query/command ports

money-app-biz (future boot/composition module) wires all implementations
```

## 已盘点调用边

| 现有调用方 | 当前依赖与行为 | 类型 | 物理拆分阻塞点 | 最小替代契约 |
| --- | --- | --- | --- | --- |
| `UmsMemberAssetExcelExportService` | `MemberCouponQueryService.countUnusedCouponsByMemberIds` | 只读批量聚合 | 接口当前位于 TRADE 源码包，UMS 因而依赖 TRADE 模块 | 将接口及 `Map<Long,Long>` 语义下沉为契约模块的 `MemberCouponCountQuery`；TRADE 保留实现和优惠券 Mapper。 |
| `CheckoutValidationService` | `UmsMemberService.getById`，将 `UmsMember` 放入 `CheckoutContext` | 结算前只读核验 | TRADE 获得 UMS Entity，后续下单和资产处理继续使用它 | `MemberCheckoutLookup.findActiveMember(id)` 返回 `MemberCheckoutSnapshot(id,name,phone)`；`CheckoutContext` 改持快照。 |
| `PosServiceImpl.listMember` | `UmsMemberService.lambdaQuery()`，并直接读取 `UmsMemberBrandLevelMapper`、`PosMemberCouponMapper` | POS 会员查询 | TRADE 同时依赖 UMS `IService<Entity>`、UMS Mapper/Entity 和会员权益数据 | `PosMemberLookup.search(keyword)` 返回现有 POS 所需的中立会员/品牌等级/券规则快照；需先确认优惠券规则详情的归属，不能把 Mapper 移入 TRADE 模块。 |
| `PosAssetActionService.consume` | 调用会员消费、余额扣减、读取会员姓名电话、写 `UmsMemberLogMapper`、更新最后到店时间 | 交易写侧协调 | TRADE 写 UMS 日志、使用 UMS Entity 和 `lambdaUpdate`；直接拆分会破坏资产操作的同事务语义 | `MemberSettlementCommand.apply(SettlementMemberAssetRequest)` 由 UMS 实现，封装消费、余额、到店时间和日志。券核销的原子性须与 TRADE 券处理共同设计，不能先机械移动。 |
| `MemberAssetFacade.restoreForRefund` | 调用 `UmsMemberAssetService.processReturn/addBalance/logVoucherRefund`，TRADE 侧恢复券状态 | 退款写侧协调 | 会员返还与优惠券恢复跨两个现有持久化所有者，且要求同一退款事务 | `MemberRefundCommand.apply(MemberRefundRequest)` 由 UMS 实现；TRADE 的券恢复通过明确的退款编排顺序接入，先由集成测试固化原子性。 |
| `OmsOrderServiceImpl.assembleOrderDetail` | `UmsMemberService.getDetail` 返回 `UmsMemberVO` | 订单详情只读拼装 | TRADE 的订单 HTTP 读模型直接依赖 UMS Web/应用 DTO | `MemberOrderProfileQuery.getForOrder(memberId)` 返回仅含订单详情所需字段的 `MemberOrderProfileSnapshot`，由 TRADE 映射到既有响应字段。 |

另有一个无行为的阻塞信号：`OmsOrderServiceImpl` 注入了 `UmsMemberBrandLevelMapper`，但当前没有使用点。它不能作为“已可拆分”的证据；后续实施前应以独立、低风险清理切片删除该死依赖并补订单详情回归。

## 分层与实施顺序

1. **先读后写。** 先下沉优惠券计数、会员结算核验和订单会员档案三个只读契约；这些不应改变数据库写入时机。
2. **再收口 POS 会员查询。** 该查询目前同时碰会员、品牌等级和券规则，是拆分 GMS/UMS/TRADE 前最复杂的只读聚合，必须先明确输出快照和数据归属。
3. **最后处理结算与退款命令。** 写侧必须保留现有 Spring 事务范围、优惠券并发拦截、会员日志和退款补偿；未有事务特征测试前不得移动。
4. **仅在所有边替换完成后评估 POM。** 此时以 `mvn -pl <candidate> -am test-compile` 和相应集成测试证明独立构建价值，再决定是否创建模块。

## 5.1 决策

阶段 5.1 的结论为：**不授权物理拆分，也不授权在本阶段直接创建空壳 Maven 模块。** 破环的最小技术前置是中立契约模块及其 DTO/接口设计；其中最小、风险最低的首个实现候选是将现有 UMS→TRADE 的 `MemberCouponQueryService` 契约下沉为中立契约，因其已是 Entity-free 的只读聚合。

下一最小任务为 **5.2：评估 GMS 的单向模块候选边界，并同时确认该中立契约模块的依赖最小集和独立编译收益**。不在 5.2 修改 POM。
