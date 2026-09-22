# 订单全额退款状态字典兼容修复

## 目标

消除订单列表读取全额退款订单时 `REFUNDED` 缺少字典项的告警，且不改写任何历史订单。

## 已确认的状态约定

| 状态码 | 展示文案 | 使用范围 |
| --- | --- | --- |
| `PAID` | 已支付 | 当前订单 |
| `PARTIAL_REFUNDED` | 部分退货 | 当前订单 |
| `REFUNDED` | 已退单 | 当前全额退款订单的规范写入值 |
| `RETURN` | 已退单 | 历史订单兼容值；不再由新退款流程写入 |

库存单据/库存流水中的 `RETURN` 是 GMS 库存领域的类型码，不属于本订单状态字典修复范围。

## 实施边界

- 新增 `V1.0.4__add_refunded_order_status_dictionary.sql`：对已存在的 `REFUNDED` 修正文案和排序；不存在时插入 `orderStatus/REFUNDED/已退单`。
- 迁移不更新 `oms_order.status`，因此历史 `RETURN` 订单及其审计值原样保留。
- 订单查询继续大小写无关地读取字典，保留 `order_status` 的空结果回退；移除临时排障 INFO 日志。未知状态仍告警并使用枚举兜底。
- 经营有效集、支付净额、班次优惠/支付、订单/商品报表、审计退款计数与会员购买历史均把历史 `RETURN` 按全额退款处理；当前只计 `PAID`/`PARTIAL_REFUNDED` 的既有分析口径保持不变。
- 不改变退款、库存、会员资产、统计公式、HTTP 接口或字典管理功能。

## 回归

`OrderStatusDictionaryIntegrationTest` 验证 Flyway 初始化后，历史 `RETURN` 与新规范 `REFUNDED` 都映射为“已退单”。
