# AD-2.50：第二十五个共享 Entity 物理归属迁移切片选择

## 结论

选择 UMS `PosMemberCoupon` 作为唯一的 AD-2.51 迁移对象。迁移前，先以 API 中立的会员券钱包查询契约替换 TRADE 对其持久化 Entity 的两处读取。

## 当前盘点

所有权登记册剩余五个共享 Entity：`OmsOrder`、`OmsOrderDetail`、`PosCouponRule`、
`PosMemberCoupon` 与 `UmsMember`。

| Entity | 所有者 | 生产面与暂缓原因 |
| --- | --- | --- |
| `OmsOrder` | TRADE | 结账、退款、订单查询、打印以及 FIN/HOME 的既有快照契约；主交易聚合与兼容查询面过宽。 |
| `OmsOrderDetail` | TRADE | 结账库存、退款返库、订单详情、打印和报表测试共同依赖；与主订单的事务闭环不可拆开。 |
| `PosCouponRule` | UMS | UMS 读模型外，还被遗留 TRADE 规则管理服务和 `/pos/couponRule` 直接以 Entity 作为请求/响应 JSON；需先单独收敛公开契约。 |
| `PosMemberCoupon` | UMS | UMS 会员导入、档案、结算、退款、充值与打印读取均为所有者资产面；仅 TRADE 券规则管理与券数量查询直接读取该 Entity。 |
| `UmsMember` | UMS | 会员档案、导入、资产、日志、充值、结账查询、FIN/HOME 快照与公开管理接口均依赖，范围最大。 |

`PosMemberCoupon` 的所有者明确为 UMS：其 Mapper 与会员资产工作流管理
`pos_member_coupon`。TRade 的两个读取方都不需要 Entity、Mapper、写入能力或 UMS 事务：

- `CouponRuleManagementServiceImpl` 仅需按会员、规则统计未使用券；
- `MemberCouponCountQueryService` 仅需按会员批量统计未使用券。

两者应改消费 UMS 提供的窄只读钱包/计数快照。遗留 `PosPrinterService` 的未使用券数量读取也应
在迁移时改用同一计数契约，避免 SYS 运行能力直接读取 UMS Entity。这样不会改变结账、退款、发券或
打印时序。

## AD-2.51 范围与约束

1. 在 API 定义 Entity-free 的会员券钱包查询契约和不可变快照：支持按会员批量统计未使用券，以及
   券规则管理所需的“规则 ID → 未使用数量”读取。UMS 提供实现。
2. TRADE 的 `CouponRuleManagementServiceImpl`、`MemberCouponCountQueryService` 与遗留
   `PosPrinterService` 改依赖该契约，不再导入 `PosMemberCoupon` 或其 Mapper；随后将 Entity 迁入
   `feature.ums.infrastructure.persistence.entity`，更新 UMS Mapper、UMS 资产/导入/档案/充值消费者及测试。
3. 保留 `@TableName("pos_member_coupon")`、AUTO ID、会员/规则/状态/订单/领取与核销时间、租户字段、
   `idx_member_id` 和 `idx_rule_id`；保留发券、导入、FIFO 核销、并发状态守卫、整单退款恢复、充值红冲、
   会员档案统计及小票余量语义。不得改变路由、DTO、表/Flyway、交易事务边界或业务公式。
4. 新增 UMS 本地 Mapper CRUD 回归；保留或扩展结账/退款、会员导入/充值、券规则卡包、POS 小票余量与
   批量计数契约回归；再完成隔离 `money_pos_test` 全量测试、打包、扫描夹具、`--check-new` 与空白检查。

## 预期架构结果

迁移完成后，登记册应从 5 降至 4。TRADE→UMS 的两条 `PosMemberCoupon` Entity 桥将由窄查询契约替代，
已登记跨域 Entity 桥预计从 4 降至 2；当前两个通配符路径不得扩大。

## 非目标

本选择不移动任何 Entity、Mapper、Service、Controller、DTO 或扫描根；不执行 AD-2.51 的生产改造。
