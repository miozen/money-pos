# MoneyPOS ME-1.6J：权益办理内嵌流水与关联单据实施计划

> 状态：**代码与自动验证完成，等待 Windows 手工验收**；决策合同：
> `MoneyPOS-ME-1.6J-Benefit-Workspace-Operation-History-Decision-Contract.md`。

## J-A：统一权益流水读模型（UMS）

1. 在 `money-app-api` 新增 Entity-free `MemberBenefitOperationHistoryVO`，含主体名称、业务动作、单位、变动前后、
   来源单号、详情目标、操作人、状态、原因和时间。
2. 在 `MemberBenefitReadService` 实现按会员和 `QUANTITY|AMOUNT|TARGET` 的限量查询；先取会员所属 ID，再以
   `IN`、倒序和数据库限制查询账本表，默认 10、最大 20。
3. 增加受保护的 `/ums/member-benefit/operation-history`，不破坏既有 `target-logs`。
4. 聚焦测试覆盖：权限、会员隔离、类型筛选、默认/最大条数、中文动作和无单据不产生详情目标。

## J-B：非订单凭证只读详情（TRADE）

1. 新增 Entity-free 凭证详情 DTO、TRADE 查询和路由，覆盖 MAR 金额权益凭证及 MTR TARGET 凭证，并复用既有支付数据。
2. 路由校验凭证类型/存在性，只返回展示字段，绝不调用打印服务。
3. 聚焦测试覆盖正确快照、错误类型/不存在和只读无副作用。

## J-C：权益工作台前端

1. 新建可复用只读流水表/详情弹窗：默认 10 条，可切换 20 条；仅在所选会员且对应入口激活时读取。
2. QUANTITY 放在“预览提货”下方，AMOUNT 放在权益余额使用区域下方，TARGET 放在计划表下方。历史可局部滚动，
   不遮挡办理、预览、确认或支付控件。
3. `ORDER` 复用订单详情，两个提货目标复用只读提货单，MAR/MTR 打开新凭证弹窗，`NONE` 显示不可点击的 `—`。
4. 既有写操作成功后的 `refresh` 同时刷新流水；失败不乐观追加。

## J-D：回归与交付

运行新增聚焦测试、隔离 `money_pos_test` 全量 Maven 回归、`mvn -q package -DskipTests`、两项架构扫描、Node 20
前端构建和 `git diff --check`；手工验收三入口的 10/20 条、所有可读单据、无单据动作、只读无副作用和 POS 状态隔离。
更新接力文档后提交、推送、fetch 并验证 `dev...origin/dev=0/0`。ME-1.6H-D 的 Windows Electron/POS 矩阵仍是
ME-1.6 总关闭条件。

## 实施事实

- J-A 已完成：UMS 新增 Entity-free `MemberBenefitOperationHistoryVO` 与受保护的
  `/ums/member-benefit/operation-history`。查询先以选中会员的权益/计划主表限定 ID，再按创建时间和 ID 倒序使用数据库
  `LIMIT` 读取账本；默认 10、服务端钳制 1..20。测试覆盖会员隔离、类型筛选、默认/最大条数、中文动作和无单据动作。
- J-B 已完成：TRADE 新增 Entity-free `MemberBenefitReceiptDetailVO` 与
  `/member-benefit/trade-history/receipt-detail`。它只读取指定的 `AMOUNT|TARGET` MAR/MTR 凭证及支付摘要，拒绝错误类型
  或不存在凭证，未引用打印服务或任何写入服务。
- J-C 已完成：三个权益面板复用 `BenefitOperationHistory`；默认 10 条，可切换至 20 条并局部滚动。只对明确的
  `ORDER`、MP/MAP 提货单、MAR/MTR 凭证开放详情；无权威单据的动作显示 `—`。每次既有写操作成功后由 Workspace 刷新概览和流水。
- J-D 自动验证已完成：隔离 `money_pos_test` 的完整 Maven 回归为 69 份 Surefire 报告、145 项测试、0 failure/error；
  `mvn -q package -DskipTests`、Node 20 `npm run build`、两项架构扫描和 `git diff --check` 均通过。前端构建仅有既有
  Browserslist/CSS/dynamic-import/chunk-size 警告。

## 当前 Next Action

在 Windows Electron/POS 中手工验收 QUANTITY、AMOUNT、TARGET 三入口的 10/20 条切换、ORDER/MP/MAP/MAR/MTR 详情、
无单据显示、只读无副作用和 POS 状态隔离；验收通过后才可关闭 ME-1.6J。
