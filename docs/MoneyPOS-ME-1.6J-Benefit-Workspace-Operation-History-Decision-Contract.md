# MoneyPOS ME-1.6J：权益办理内嵌流水与关联单据决策合同

> 状态：**已确认，授权实施**；确认日期：2026-10-12；适用分支：`dev`。
>
> 本合同只补足权益办理工作台的只读流水和单据查看能力，不改变权益办理、提货、库存、支付、退款、TARGET
> 贡献或会员等级的任何写入规则。

## 1. 目标与范围

| 入口 | 流水位置 | 覆盖事件 |
| --- | --- | --- |
| 商品寄存（QUANTITY） | “预览提货”操作区下方 | 寄存办理、会员提货、未提货权益退款扣回 |
| 品牌权益包（AMOUNT） | 权益余额使用/预览区域下方 | 权益包办理、金额权益提货、提货退款恢复 |
| 会员升级计划（TARGET） | 计划列表下方 | 建立、订单计入、补差、豁免、人工确认、取消、退款待复核 |

1. 默认显示最近 **10** 条；“查看 20 条”只扩大同一只读查询的上限。服务端必须钳制为 `1..20`，不允许无上限读取。
2. 同一类型下的多份权益/计划必须显示商品、权益包或升级计划等主体名称，不能只显示笼统类型。
3. 每行最少显示：时间、主体、业务、变动值、变动后值、关联单据、操作员。数量权益使用“件”，金额权益使用“元”，
   TARGET 使用“进度”。
4. 只读取既有权益账本；不得复制、补写或伪造 `ums_member_log`，不得创建库存流水、支付、订单、凭证或打印副作用。

## 2. 单据关联冻结规则

只关联实际持久化的权威来源。没有可读单据的操作显示 `—`，不得按编号前缀猜测或硬套普通订单详情。

| 来源 | 详情目标 | 行为 |
| --- | --- | --- |
| QUANTITY `GRANT` / `TRADE_ORDER`（QDP） | `ORDER` | 既有普通订单详情 |
| QUANTITY `PICKUP_DEDUCT` / `MEMBER_PICKUP`（MP） | `QUANTITY_PICKUP` | 既有只读数量提货单，不打印 |
| QUANTITY `REFUND_CANCEL` / `TRADE_REFUND` | `ORDER` | 来源普通订单详情 |
| AMOUNT `GRANT` / `BUSINESS_RECEIPT`（MAR） | `AMOUNT_RECEIPT` | 新的只读金额权益凭证详情，不打印 |
| AMOUNT `PICKUP_DEDUCT` / `MEMBER_AMOUNT_PICKUP`（MAP） | `AMOUNT_PICKUP` | 既有只读金额提货单，不打印 |
| AMOUNT 提货退款恢复且无独立可读凭证 | `NONE` | 显示 `—`，保留流水/原因 |
| TARGET 销售贡献或退款复核且来源为普通订单 | `ORDER` | 既有普通订单详情 |
| TARGET 补差/豁免凭证（MTR） | `TARGET_RECEIPT` | 新的只读 TARGET 凭证详情，不打印 |
| TARGET 建立、人工确认、取消 | `NONE` | 显示 `—`，保留原因/操作人 |

MAR/MTR 详情只展示凭证头、支付摘要（如有）、金额、状态、原因、时间和操作员。它们不是普通订单，不得打开订单详情、
开钱箱或重复打印。

## 3. 架构与不变项

1. 新增 UMS 所有的 `GET /ums/member-benefit/operation-history`，参数 `memberId`、
   `benefitType=QUANTITY|AMOUNT|TARGET` 与可选 `limit`；权限沿用 `memberBenefit:operate/manage`，返回 Entity-free
   `MemberBenefitOperationHistoryVO`。
2. UMS 先以会员权益/计划主表限定 ID，再按时间倒序、受限 `limit` 查询账本表；不得全表读出后在内存筛选。
3. 新增 TRADE 所有的受保护只读 MAR/MTR 凭证详情快照/路由；UMS 不得引用 TRADE Entity、Mapper 或实现类。
4. 现有 `target-logs` 保持兼容，但不作为此工作台的流水源；会员全景画像聚合也不复用，以免混入 TRADE 文档重复行。
5. 不新增 Flyway 迁移，不改历史数据、单据编号、事务边界、权限模型、QDP/MP/MAP/MAR/MTR 创建与打印规则。
