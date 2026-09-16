# MoneyPOS Entity 归属与跨域 DTO 计划

本文件是阶段 4.2 的**逻辑归属表**，不是实体搬迁或数据库重构计划。所有 Entity 目前仍在共享 API/System 包，表名、MyBatis 映射、Flyway、Controller 路由和已有服务签名均不因本文件改变。

## 归属判定规则

归属按写入责任、业务生命周期和当前主要应用服务决定，而不是按包位置或类名前缀单独判断。一个 Feature 在本域内部使用其归属 Entity 是允许的；跨域服务接口、facade 返回值和 HTTP 契约则应逐步改为场景 DTO。

| 逻辑归属 | Entity | 依据与当前处理 |
| --- | --- | --- |
| GMS 商品/库存 | `GmsBrand`、`GmsGoods`、`GmsGoodsCategory`、`GmsGoodsCombo`、`GmsInventoryDoc`、`GmsInventoryDocItem`、`GmsInventoryOrder`、`GmsInventoryOrderDetail`、`GmsStockLog`、`GmsTurnoverWarningSnapshot`、`PosSkuLevelPrice` | 商品目录、价格、库存、单据和周转服务拥有写入责任；TRADE/FIN/HOME 读取时不得新增这些实体作为跨域契约。 |
| UMS 会员/权益 | `UmsMember`、`UmsMemberBrandLevel`、`UmsMemberLog`、`UmsRechargeOrder`、`PosCouponRule`、`PosMemberCoupon`、`PosMemberLevel`、`GmsMemberTransaction` | 会员、充值、券和等级由会员/权益流程维护。`GmsMemberTransaction` 名称历史遗留但字段为会员余额流水，暂归 UMS；当前没有 Feature 应用层调用它。 |
| TRADE 订单 | `OmsOrder`、`OmsOrderDetail`、`OmsOrderLog`、`OmsOrderPay`、`OmsRefundIdempotent` | 收银、订单、支付、退款和幂等记录由 TRADE 写入；FIN/HOME 的报表读取不应反向采用订单实体作为新契约。 |
| HOME 读模型 | `OmsDailySummary` | 虽带 `Oms` 前缀，但目前唯一应用写入/读取责任在 HOME 决策快照；暂按 HOME 读模型处理，后续若由 TRADE 产出再重新评估。 |
| SYS/平台参考数据 | `SysBrandConfig`、`SysPrintConfig`、`SysStrategy`、`SysDict`、`SysDictDetail`、`SysPermission`、`SysRole`、`SysRolePermissionRelation`、`SysTenant`、`SysUser`、`SysUserRoleRelation`、`Provinces` | 系统配置、租户、权限与参考数据保持 SYS 兼容层；业务 Feature 只应消费所需值或专用查询 DTO。 |

## 跨域契约优先级

| 优先级 | 现有风险与证据 | 目标场景 DTO | 最小迁移切片与验收 |
| --- | --- | --- | --- |
| P0 | `PosService.getValidCouponRules()` 和 `/ums/member/coupon-rules` 直接暴露 `PosCouponRule`。该规则逻辑归 UMS，却被 TRADE 的 POS 服务和 UMS 路由共同使用。 | `CouponRuleSummary`：规则 ID、名称、门槛、优惠额、状态；不暴露持久化字段。 | 将 POS 查询结果映射为 DTO，保持 JSON 字段兼容或同步更新前端契约；覆盖优惠券获取、试算、核销与退款恢复。 |
| P1 | TRADE 的 POS/checkout 通过 `GmsGoodsService extends IService<GmsGoods>`、`UmsMemberService extends IService<UmsMember>` 和共享 Mapper 取得商品/会员/价格/券实体；结算测试已覆盖这条高一致性路径。 | `GoodsSnapshot`、`MemberSnapshot`、`MemberBenefitSnapshot`，以及既有库存/资产 facade DTO 的补充版本。 | 先在 GMS/UMS 提供只读快照接口，再让 TRADE 的查询/校验路径消费 DTO；保留事务写侧 facade，不在同一切片迁移 Mapper 或实体。运行 CheckoutIntegrationTest 与全量测试。 |
| P2 | FIN `FinanceDashboardAssembler` 组合 `GmsInventoryDoc`、`OmsOrder`、`UmsMemberLog`；HOME 读取 `OmsOrder`，均属读模型跨域使用。 | `FinanceDashboardSnapshot`、`HomeSalesSnapshot`。 | 以只读报表 DTO 替换 assembler/查询边界，保持 SQL、图表字段和快照写时机；验证 FIN/HOME 集成测试与页面回归。 |
| P3 | GMS/UMS/SYS 内部服务和 Mapper 使用本域或兼容 Entity；`GmsMemberTransaction` 尚无 Feature 应用层消费者。 | 无（先保持内部实现）。 | 仅在实际新跨域调用出现时创建场景 DTO；不为“包整洁”单独搬迁实体。 |

## 已识别的相关兼容债务

- `GmsGoodsService` 与 `UmsMemberService` 继承 `IService<Entity>`，因此其跨域调用者可以取得本域持久化实体。P1 应优先新增窄读接口，而不是移除 `IService` 或更改全部既有调用。
- `UmsMemberService.getTop20Goods()` 和 `UmsMemberController` 暴露 `UmsMemberServiceImpl.MemberGoodsRankVO`。这不是 Entity，但属于实现类嵌套类型泄漏；在会员画像 DTO 切片中一并改为顶层 DTO，不作为 4.2 的实体迁移。
- `PosCouponRule`、`PosMemberCoupon` 与 `PosSkuLevelPrice` 带有历史 `Pos` 前缀；归属以写入责任而非名称决定，分别是 UMS 权益和 GMS 价格。TRADE 只能通过明确查询/交易契约使用它们。

## 执行约束

1. 不移动共享 Entity 的物理包，不改表、Mapper、Flyway 或 API 路由。
2. 每个 DTO 切片只覆盖一个业务场景，先保留旧契约并补回归，再逐步收敛调用方。
3. 未完成迁移的 Entity 导入仍是可追踪基线，不进入阻断门禁；新增跨域 Entity 契约必须在阶段 4 清单和台账说明原因。
4. P0/P1 完成后再将对应扫描从“报告”升级为“新增违规门禁”。
