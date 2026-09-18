# AD-1.3a HOME 日快照命令实现记录

## 目标与边界

本轮把 HOME `OmsDailySummary` 的快照写入从仪表盘读取组装中抽离，同时严格保持
`GET /home/count` 的既有触发时序：先补偿此前七个自然日的缺失快照，再刷新当天快照，
最后读取并组装仪表盘。HTTP 路由、响应字段、数据库表、Flyway 与跨 Feature 查询契约
均不改变。

## 实施结果

- `HomeDailySnapshotAssembler` 只通过既有 TRADE、GMS、UMS 查询契约计算不可变日快照值。
- `HomeDailySnapshotCommandService` 区分历史缺失补偿和当天刷新；前者采用数据库唯一键
  `uk_record_date` 上的 insert-if-absent，后者采用原子 upsert，消除原先查询后再
  insert 的竞争窗口。
- `HomeDailySummaryWriter` 仅写 HOME 自有的 `OmsDailySummary`；当天 upsert 只覆盖由该
  快照负责的指标，保留既有 `member_recharge` 值。
- `HomeDashboardQueryService` 承担无写入的仪表盘读取与告警组装；`DecisionEngine` 退化为
  保持旧 GET 行为的兼容外观层。

## 验收与遗留

`HomeCountSnapshotCharacterizationTest` 覆盖 GET 响应形状、当天创建和覆盖、七日补偿边界、
订单口径以及 `member_recharge` 保留。重复历史补偿不会覆盖已有行；并发同日写入由数据库
唯一键与 upsert 保证单行，最后完成的刷新值生效。

本轮刻意不让 GET 变为纯读。该调整属于 AD-1.3b，必须先由用户选择定时刷新或订单事务事件
刷新，并接受相应的数据新鲜度语义。
