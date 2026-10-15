# GMS-OPT-1：周转预警快照读写分离实施契约

## 1. 目标与范围

本契约落实 `GMS-OPT-1`：将 GMS 周转预警的日快照写入从 HTTP 查询链路中移出。

- `GET /gms/analysis/turnover-warnings`、其补货/滞销导出，以及趋势查询只读；
- GMS 自有的显式命令负责生成或更新当天快照；
- 应用就绪后执行一次，之后以五分钟 fixed-delay 刷新一次；同一 JVM 内不允许重叠刷新；
- 调度失败记录错误日志，并保留上一次有效快照，不影响启动或 HTTP 查询。

该刷新频率保持原先“访问分析页后可得到当日最新快照”的时效预期，同时撤销 GET 的写权限。它不改变 HOME 的已验收任务，也不改变 Electron 启动流程。

## 2. 必须保持不变的业务契约

- 周转扫描 SQL、预警阈值、排序、Top 20 截取和返回 DTO 不变；
- 所有既有路由、响应形状和导出内容不变；
- 继续使用 `gms_turnover_warning_snapshot` 及其 `snapshot_date` 唯一键；不新增迁移，不补写历史数据；
- GMS 快照表继续是全局读模型（Mapper 保持 tenant-line ignore），不引入跨 Feature 实体或 Mapper 依赖；
- 一天内刷新仍更新同一条记录；单机部署与任务内 `AtomicBoolean` 防止同一进程内重叠执行。

## 3. 最小实现设计

1. `GmsTurnoverService` 只计算并返回当前预警看板，移除快照写入依赖。
2. 新增 GMS 内部 `GmsTurnoverSnapshotCommandService`：调用上述只读计算，组装快照，并在一个事务中按当天记录插入或更新。
3. 新增 `GmsTurnoverSnapshotRefreshTask`：监听 `ApplicationReadyEvent`，并使用五分钟 fixed-delay 调度命令；异常在调度边界可观测地记录，不在写入代码中静默吞掉。
4. 保持数据库唯一键作为日快照幂等边界。当前产品为单机单应用进程，任务内互斥覆盖实际调度并发范围；不额外引入分布式锁或新基础设施。

## 4. 验收与非目标

验收至少证明：

- 连续查询预警不创建或更新快照；
- 显式刷新创建当天一条快照，后续刷新更新同一条；
- 趋势仍按日期读取既有 JSON 快照；
- GMS 快照调度不重叠，失败不向 HTTP 查询传播。

非目标：不修改库存扣减并发控制（GMS-OPT-2）、商品删除完整性（GMS-OPT-3）或路由权限（GMS-OPT-4），不改预警公式、SQL、迁移和前端。
