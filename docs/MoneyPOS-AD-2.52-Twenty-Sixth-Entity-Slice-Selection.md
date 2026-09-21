# AD-2.52：第二十六个共享 Entity 物理归属迁移切片选择

## 结论

选择 UMS `PosCouponRule` 作为唯一的 AD-2.53 迁移对象。迁移前须先以 API 中立的规则管理命令/查询
契约替换遗留 TRADE 服务和 `/pos/couponRule` 对持久化 Entity 的直接暴露，再移动 Entity。

## 当前盘点

`PosMemberCoupon` 已在 AD-2.51 迁至 UMS 后，所有权登记册剩余四个共享 Entity：`OmsOrder`、
`OmsOrderDetail`、`PosCouponRule` 与 `UmsMember`。

| Entity | 所有者 | 生产面与暂缓/选择结论 |
| --- | --- | --- |
| `OmsOrder` | TRADE | 结账、退款、订单查询、打印及 FIN/HOME 快照均读取主订单；主交易聚合和兼容查询面过宽，暂缓。 |
| `OmsOrderDetail` | TRADE | 结账库存、退款返库、订单详情、打印和报表共用，且与主订单的事务闭环不可拆开，暂缓。 |
| `PosCouponRule` | UMS | Mapper、会员卡包与结账优惠读模型均为 UMS 资产；外部仅有遗留 TRADE 规则管理服务直接读写，并由 `/pos/couponRule` 直接以 Entity 作为 JSON 请求/响应。这是可先收敛为窄契约的最小切片，选定。 |
| `UmsMember` | UMS | 会员档案、导入、资产、日志、充值、结账、FIN/HOME 快照和公开管理接口均依赖，范围最大，暂缓。 |

`PosCouponRule` 的持久化所有者明确为 UMS：`PosCouponRuleMapper`、
`PosMemberBenefitQueryService` 和 `CheckoutPricingBenefitQueryService` 管理或读取
`pos_coupon_rule`。TRADE 的 `CouponRuleManagementServiceImpl` 目前仅为遗留管理端提供分页、增删改和
会员卡包组装；它不应拥有 UMS 券规则的持久化能力。两条登记的 TRADE→UMS Entity 桥正是该服务接口和实现的
`PosCouponRule` 依赖。

管理端的兼容面必须被视为公开契约：`/pos/couponRule` 的列表、POST、PUT、DELETE 和
`/memberCoupons/{memberId}` 路由，以及现有 JSON 字段、分页筛选和排序，均不可在迁移中改变。
`PosCouponRuleControllerIntegrationTest` 已覆盖规则增删改、分页查询和卡包数量；结账优惠快照与会员 POS
展示也分别有既有集成回归。未发现资源文件中残留的旧 Entity FQCN。

## AD-2.53 范围与约束

1. 在 API 定义 Entity-free 的券规则管理查询、命令及不可变请求/响应快照；UMS 提供实现。TRADE 的遗留
   服务和 Controller 只负责兼容 HTTP 编排或直接消费该契约，不得再导入 `PosCouponRule` 或其 Mapper。
2. 在保持 `/pos/couponRule` 既有请求/响应 JSON 字段的前提下，移除 Controller/TRADE 服务的 Entity
   签名；将 `PosCouponRule` 迁入 `feature.ums.infrastructure.persistence.entity`，更新 Mapper、UMS
   会员卡包、结账优惠读模型和受影响测试。
3. 保留 `@TableName("pos_coupon_rule")`、AUTO ID、全部规则/审计/租户字段、表定义和种子规则；保留名称
   like 筛选、按创建时间倒序分页、增删改、未使用券卡包计数、结账门槛/折扣计算、路由、DTO/Flyway 与既有
   事务边界。
4. 新增 UMS 本地 Mapper CRUD 和规则管理契约回归；保留/扩展 Controller 的 JSON 与分页/卡包回归、
   `CheckoutPricingBenefitQueryService`、会员 POS 展示以及结账优惠回归；再完成隔离
   `money_pos_test` 全量测试、打包、扫描夹具、`--check-new` 与空白检查。

## 预期架构结果

迁移完成后，登记册应从 4 降至 3；两条 TRADE→UMS 的 `PosCouponRule` Entity 桥将由 UMS 券规则契约
替代，已登记跨域 Entity 桥预计从 2 降至 0。当前两个通配符路径不得扩大。

## 非目标

本选择不移动任何 Entity、Mapper、Service、Controller、DTO 或扫描根；不执行 AD-2.53 的生产改造。

## AD-2.53 实施结果

`PosCouponRule` 已迁至 `feature.ums.infrastructure.persistence.entity`。新增 API 中立的
`CouponRuleManagementQuery`、`CouponRuleManagementCommandHandler` 及请求/响应/卡包快照；UMS 负责
实现查询、增删改与事务，遗留 TRADE 服务仅委托该契约，`/pos/couponRule` 的路由和 JSON 字段保持不变。
UMS Mapper CRUD、规则管理的分页/JSON/卡包、结账优惠与会员 POS 既有回归均覆盖；表、种子、Flyway、
筛选排序和事务语义未变。
