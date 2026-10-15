# MoneyPOS FCO-2：UMS 一致性审查记录

> 状态：**只读审查完成，未实施优化**；审查日期：2026-10-15；适用分支：`dev`。

## 现状地图

| 子域 | 所有者与主要实现 | 关键事实 |
| --- | --- | --- |
| 会员档案/品牌等级 | `UmsMemberProfileService`，`ums_member`、`ums_member_brand_level` | 档案 CRUD、逻辑注销、品牌等级；向 GMS 仅调用 `BrandNameQuery`。 |
| 资产与充值 | `UmsMemberAssetService`、`UmsMemberRechargeService`，会员余额/券、`ums_recharge_order`、`ums_member_log`、券钱包 | 充值、撤销、消费与退款均在本地事务中；充值单号有 `uk_order_no`。 |
| 权益账本 | `MemberBrandBenefitLedgerService` 及数量/金额/TARGET 表 | 使用 Entity-free API 命令与 TRADE/GMS 协作；独立权益权限已由 V1.0.10 冻结。 |
| 读模型与运营 | 导入/导出、券钱包、权益历史、HOME/FIN 查询 | 主要通过 UMS 自有查询服务或 API DTO 输出。 |

主要入口是 `UmsMemberController`、`UmsMemberAssetController`、`UmsMemberImportController`、
`UmsMemberBenefitController` 和 `PosMemberTargetPlanController`。会员 CRUD 使用 `umsMember:*`，充值/红冲使用
`umsMember:recharge`/`umsMember:void`，权益使用 `memberBenefit:*` 或 `pos:cashier`。现有测试覆盖充值红冲、权益账本、
导入/导出、券钱包、档位、路由和若干 TRADE 集成路径。

## 已确认约束

1. UMS 是会员档案、余额/券、充值、券钱包和权益账本的写入所有者；TRADE 仅经 `money-app-api` 命令协作。
2. 结算、退款与提货保持既有 TRADE 总事务；不得在 UMS 优化中迁移交易所有权或金额口径。
3. `ums_recharge_order.order_no` 有数据库唯一键；它是服务端凭证号，不是已冻结的客户端重试幂等键。
4. 会员注销当前为逻辑删除并清零资产；历史订单/流水仍保留，因此任何改变须有明确审计兼容设计。

## 已确认问题

| 编号 | 影响 | 证据 | 风险 |
| --- | --- | --- | --- |
| UMS-1 | 中 | `UmsMemberLogController` 的 `/ums/member-log` 读取入口无 `@PreAuthorize`，而会员、资产、权益入口均有明确权限。 | 已认证但无会员查看权限的用户可读取敏感资金流水。 |
| UMS-2 | 高 | `UmsMemberProfileService.delete` 逻辑注销会员时直接清零余额与券，并不检查未撤销充值、未使用券、进行中权益或 TARGET 计划。 | 管理员删除可使未结资产脱离有效会员档案，破坏撤销、履约和审计可追溯性。 |

## 最小优化候选

1. **UMS-OPT-1：资金流水权限对齐**——为 `/ums/member-log` 复用 `umsMember:list`，加路由权限契约测试；不新增权限或角色授权。
2. **UMS-OPT-2：会员注销资产完整性守卫**——先明确“注销”是否应拒绝有未结资产/权益的会员；若确认，只新增只读存在性检查并拒绝，不改历史数据或级联删除。

UMS-OPT-1 是唯一不改变业务规则的候选，建议优先。UMS-OPT-2 涉及已存在管理行为，须先由业务方明确可注销的资产/权益状态集合。
