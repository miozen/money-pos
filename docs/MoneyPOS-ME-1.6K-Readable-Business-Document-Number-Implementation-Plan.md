# MoneyPOS ME-1.6K：可读业务单据编号实施计划

> 状态：**自动化验收完成，待手工验收**；唯一决策合同：
> `MoneyPOS-ME-1.6K-Readable-Business-Document-Number-Decision-Contract.md`。

## K-A：TRADE 序列与普通订单幂等分离

1. 新增 Flyway 序列表及 `oms_order.request_no` 唯一索引；新增 TRADE 内部事务性编号生成器。
2. 普通 Checkout 使用请求号查重、服务端生成 `REQ` 或 `QDP` 业务 `order_no`；旧订单兼容旧查询。
3. 覆盖格式、同毫秒/同日序号、QDP 前缀和普通订单重复请求。

**完成事实：** `trade_business_document_sequence` 以租户、前缀和业务日期为唯一键，使用数据库原子递增维护日序号；
`oms_order.request_no` 保留请求幂等性，`order_no` 改由服务端生成 `REQ`/`QDP` 可读业务号。历史订单仍可用原 `order_no`
完成重放查询。Checkout 集成测试已改为“请求号发起、响应业务单据号用于后续详情/退款”。

## K-B：权益单据接入

1. MP、MAP、MAR/MARF、MTR 改用同一生成器，保留各自已有 `request_no` 幂等校验。
2. 覆盖六种前缀、重复请求和不改变关联账本/支付/库存写入。

**完成事实：** QUANTITY 提货 MP、AMOUNT 提货 MAP、AMOUNT 凭证 MAR/MARF、TARGET 凭证 MTR 均使用统一生成器；
既有请求号幂等、账本、支付、库存和事务边界保持不变。

## K-C：回归与交付

运行隔离 `money_pos_test` 全量 Maven 回归、package、两项架构扫描、Node 20 前端构建和 `git diff --check`；
手工验证新旧单据详情、打印和退款。更新接力记录、提交、推送、fetch 并确认 `dev...origin/dev=0/0`。

**原自动化完成事实：** 干净隔离库从 V1.0.0 迁移至 V1.0.16 后，Checkout、QUANTITY、AMOUNT、TARGET 聚焦集成回归共
36 项通过；全量 Maven 回归 69 份报告、0 failure、0 error；`mvn -q package -DskipTests`、Node `v20.20.2`
生产构建、两项架构扫描和 `git diff --check` 通过。前端构建仅保留既有 Browserslist/CSS/dynamic-import/chunk-size 警告。

## 首个 Next Action

实施 2026-10-12 已确认的格式修订：去除毫秒、日序号改四位；更新格式测试并重新执行自动化门禁。

## K-D：编号长度修订（2026-10-12 已确认）

1. 新生成业务号从 `<前缀><yyyyMMddHHmmssSSS><六位当日序号>` 改为
   `<前缀><yyyyMMddHHmmss><四位当日序号>`。
2. 序列归属保持租户、前缀、自然日独立；每天从 `0001` 开始，最多 `9999`，不改变表结构、幂等键或历史单据。
3. 更新格式断言并完整复跑隔离 Maven、package、Node 20、架构扫描和差异检查；重新提交推送后才进入手工验收。

**完成事实：** 新生成单据已使用秒级时间与四位日序号，Checkout 格式断言通过。隔离 `money_pos_test`
全量 Maven 回归为 69 份报告、0 failure、0 error；package、Node `v20.20.2` 构建、两项架构扫描和差异检查通过。
