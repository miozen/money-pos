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
| P0（已完成） | `PosService.getValidCouponRules()` 和 `/ums/member/coupon-rules` 原先直接暴露 `PosCouponRule`。该规则逻辑归 UMS，却被 TRADE 的 POS 服务和 UMS 路由共同使用。 | `CouponRuleSummary`：规则 ID、名称、门槛、优惠额、状态；不暴露持久化字段。 | 已由 POS 查询映射 DTO，并保持既有 JSON 字段名；`UmsMemberPosControllerIntegrationTest` 覆盖读取结果，2026-09-16 全量测试和打包均通过。优惠券试算、核销与退款恢复继续由既有结算回归覆盖。 |
| P1 | TRADE 的 POS/checkout 通过 `GmsGoodsService extends IService<GmsGoods>`、`UmsMemberService extends IService<UmsMember>` 和共享 Mapper 取得商品/会员/价格/券实体；结算测试已覆盖这条高一致性路径。 | `GoodsSnapshot`、`MemberSnapshot`、`MemberBenefitSnapshot`，以及既有库存/资产 facade DTO 的补充版本。 | 先在 GMS/UMS 提供只读快照接口，再让 TRADE 的查询/校验路径消费 DTO；保留事务写侧 facade，不在同一切片迁移 Mapper 或实体。运行 CheckoutIntegrationTest 与全量测试。 |
| P2 | FIN `FinanceDashboardAssembler` 组合 `GmsInventoryDoc`、`OmsOrder`、`UmsMemberLog`；HOME 读取 `OmsOrder`，均属读模型跨域使用。 | `FinanceDashboardSnapshot`、`HomeSalesSnapshot`。 | 以只读报表 DTO 替换 assembler/查询边界，保持 SQL、图表字段和快照写时机；验证 FIN/HOME 集成测试与页面回归。 |
| P3 | GMS/UMS/SYS 内部服务和 Mapper 使用本域或兼容 Entity；`GmsMemberTransaction` 尚无 Feature 应用层消费者。 | 无（先保持内部实现）。 | 仅在实际新跨域调用出现时创建场景 DTO；不为“包整洁”单独搬迁实体。 |

## 已识别的相关兼容债务

- `GmsGoodsService` 与 `UmsMemberService` 继承 `IService<Entity>`，因此其跨域调用者可以取得本域持久化实体。P1 应优先新增窄读接口，而不是移除 `IService` 或更改全部既有调用。
- `UmsMemberService.getTop20Goods()` 和 `UmsMemberController` 暴露 `UmsMemberServiceImpl.MemberGoodsRankVO`。这不是 Entity，但属于实现类嵌套类型泄漏；在会员画像 DTO 切片中一并改为顶层 DTO，不作为 4.2 的实体迁移。
- `PosCouponRule`、`PosMemberCoupon` 与 `PosSkuLevelPrice` 带有历史 `Pos` 前缀；归属以写入责任而非名称决定，分别是 UMS 权益和 GMS 价格。TRADE 只能通过明确查询/交易契约使用它们。

## P1.6 调用面复核（2026-09-16）

- 已经由 API 契约收敛的 P1 场景包括：结账商品读取、POS 商品目录、结账会员核验、订单/收银会员档案、会员未使用券统计，以及会员结算和退款资产写入。它们的调用方不再以 GMS/UMS Entity、Mapper 或 `IService<Entity>` 作为场景契约。
- 当前 67 个 Feature 文件仍导入共享 Entity；其中大部分是归属 Feature 内部持久化实现，不能以总数判断跨域风险。`PosMemberCoupon` 虽有 POS 前缀，仍归 UMS；`PosSkuLevelPrice` 仍归 GMS。
- 未关闭的跨域兼容面以 `MoneyPOS-P1.6-Call-Surface-Review.md` 为准：库存写侧已收敛为 GMS 命令，剩余是结算试算、POS 会员券/品牌展示、会员档案品牌展示及 P2 的 FIN/HOME 读模型。它们不应被误记为已完成的 P1 场景。

## 执行约束

1. 不移动共享 Entity 的物理包，不改表、Mapper、Flyway 或 API 路由。
2. 每个 DTO 切片只覆盖一个业务场景，先保留旧契约并补回归，再逐步收敛调用方。
3. 未完成迁移的 Entity 导入仍是可追踪基线，不进入阻断门禁；新增跨域 Entity 契约必须在阶段 4 清单和台账说明原因。P1.6 起，任何新增的跨 Feature 实现包 import 会被本地 `architecture-scan.sh --check-new` 阻止；场景契约应放在 `money-app-api` 的 `com.money.contract` 下。
4. P0 已完成，Controller-Mapper、跨 Feature `ServiceImpl`/Mapper 与 `platform → feature` 三项结构扫描可进入“仅新增违规”门禁实施评估；共享 Entity 跨域规则仍等待 P1 的窄读接口后再升级。
