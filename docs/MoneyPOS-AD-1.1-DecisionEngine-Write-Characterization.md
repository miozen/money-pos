# MoneyPOS AD-1.1：HOME `DecisionEngine` 日快照写入特征化

## 结论

`DecisionEngine` 的跨所有者读取已在 P2 关闭，但其 HOME 自有
`OmsDailySummary` 写入边界尚未治理。当前唯一生产触发器是
`GET /home/count`：一次读取请求会补偿过去七天缺失快照、无条件重算当天快照，并在
同一请求中读取七日均值、生成告警和组装月/年/总计。

本 AD-1.1 只特征化现状，未改变 HTTP 路由、表、Flyway、事务边界、写入时机或告警公式。

## 调用与写入时序

```text
GET /home/count
  -> DecisionEngineService.getComprehensiveDashboard()
  -> getTodayDashboardWithAlerts()
     -> compensateSnapshots(7)
        -> 对 today-1 至 today-7：不存在才 generateDailySnapshot(date)
     -> generateDailySnapshot(today)                 // 每个请求都执行
     -> 读取当天快照和过去七天（不含当天）的均值，生成告警
  -> 查询月/上月、年/上年和总计订单快照，组装响应
```

生产代码中 `getComprehensiveDashboard` 的唯一调用方是 `HomeController`；没有调度器、
消息消费者或其他生产调用方。`DecisionEngineServiceImpl` 及其接口方法没有
`@Transactional` 注解。

## 快照口径与写入规则

| 行为 | 当前规则 | 必须在 AD-1.2 保留或显式迁移 |
| --- | --- | --- |
| 历史补偿窗口 | 仅检查 `today-1` 到 `today-7`；超过七天的缺失快照不补偿。已有历史行不重算。 | 七天窗口、缺失才补、超过窗口不触碰。 |
| 当天重算 | 每次 `/home/count` 都调用 `generateDailySnapshot(today)`。 | 页面刷新会纠正当天快照，不能静默改成只读。 |
| 订单输入 | TRADE `HomeDailyOrderSnapshot`，闭区间当日，当前仅 `PAID`/`PARTIAL_REFUNDED`；HOME 计算利润和 ASP。 | 状态集、日期边界、利润/ASP 舍入。 |
| 会员/库存输入 | UMS `HomeDailyMemberQuery` 按日期统计新会员；GMS `InventoryValuationQuery` 返回当前库存价值。 | 历史补偿使用的是运行时读取到的库存值，不能误写成历史日终库存。 |
| 持久化 | 按 `record_date` 先查询：存在即 `updateById`，不存在即 `insert`。 | 同一日期的更新覆盖语义与现有字段。 |
| 告警均值 | 直接读 HOME `oms_daily_summary`，范围 `[today-7, today)`；当天快照写入后才读取。 | 均值窗口、告警字段与零值规则。 |

`oms_daily_summary.record_date` 已有 `uk_record_date` 唯一索引。因此数据库保证一日至多一行；
这不是应用层并发协调。

## 已固化的回归证据

`HomeCountSnapshotCharacterizationTest` 现在覆盖：

1. 删除当天行后，首次 `/home/count` 创建快照；人工篡改后再次读取会保留同一主键并覆盖数值；
   同日顺序重复读取最终仍只有一行。
2. 删除 `today-7` 和当天行后，`/home/count` 会补齐最老允许补偿日并重建当天；人为保留的
   `today-8` 快照不会被重算，证明补偿窗口严格为过去七日。
3. 日快照与综合大盘维持不同订单状态口径，且新会员、库存、警报和响应字段的既有特征测试
   继续存在。

## 并发窗口与风险

当前未以非确定性并发测试来掩盖问题；以下风险由生产调用图、无事务/锁注解和 SQL 结构直接
确定，AD-1.2 必须决定处理方式：

| 窗口 | 现有保护 | 风险 |
| --- | --- | --- |
| 两个请求同时发现某历史日缺失 | `record_date` 唯一索引 | 两方都可经过 `exists` 与后续 `selectOne` 的检查；其中一方插入可能因唯一键失败。代码没有捕获/重试或数据库 upsert。 |
| 两个请求同时重算当天已有行 | 无乐观版本、锁或单飞协调 | 两方可基于不同时刻的 TRADE/UMS/GMS 输入覆盖同一行，结果是最后写入者获胜。 |
| 单次快照的多所有者输入 | 无跨所有者一致性事务 | 订单、会员和库存读可来自不同瞬间；历史补偿尤其会写入“当前库存估值”。 |
| HTTP GET 重试、浏览器并发页签或负载均衡 | 每次请求都写当天 | 请求量直接放大写入次数和上述竞争机会。 |

唯一索引使“重复行”不是主风险；主风险是并发首次插入报错和无版本覆盖。不能仅删除
`generateDailySnapshot(today)` 来修复，因为那会改变当前页面刷新即重算的兼容契约。

## AD-1.2 的决策输入

下一步应只设计、不要直接实施下列选择：

1. 保留 GET 写入作为短期兼容层，还是把补偿/当天重算迁到显式 HOME 命令与调度入口；
2. 以数据库 upsert、每日期锁、乐观版本或请求内单飞中的哪一种实现幂等；
3. 是否允许新增 Flyway 变更（当前唯一键已存在，但没有版本列或 upsert SQL）；
4. 历史补偿的库存字段是否维持“当前估值”兼容行为，还是需要独立的历史库存语义；
5. 如何使读响应在迁移期间继续返回同样的 today/month/year/total/alerts 字段。

在这些选择获得明确授权前，`DecisionEngine` 的写入实现不得移动或改变。
