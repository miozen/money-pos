# MoneyPOS OMNIA-CLOUD-7 单店分区同步简化实施契约

> 状态：已完成；确认及完成日期：2026-10-22；适用分支：`dev`。

## 目标与边界

将已完成的会员快照同步基础简化为个人单店可操作的模式，同时保留 `store_id` 作为 D1 数据分区。MariaDB 始终为唯一业务权威；云端只接收和查询快照，不参与交易或任何本地写入。

## 冻结决定

- POS 的 `storeId` 保留为本机配置字段、默认 `store-01` 并在后台显示；它不得写死在业务代码。真机验收可使用 `store-01-test`，同一 D1 以 `(store_id, member_id)` 隔离测试和生产快照。
- Worker 以一个 Secret 的 `storeId -> 上传密钥集合` 验证上传；取消 POS 的 Key ID 配置和 Worker 对 D1 `sync_clients` 的运行时依赖。既有 D1 表和初始迁移不删除、不改写。
- Worker 查询网页和 Telegram 后续固定生产查询分区 `store-01`，不接受浏览器或 Telegram 请求指定任意 storeId。
- 首次同步自动创建/恢复持久化回填检查点。已完成检查点在重新启用同步时不重复扫描；回填和普通业务变更共用 Outbox 版本合并。
- 配置更换 storeId、端点或上传密钥后，不自动重投历史数据。仅本机超级管理员可显式启动“数据修复”，以当前会员状态重入 Outbox；修复必须同时覆盖活跃会员 UPSERT 与已删除/已合并会员 TOMBSTONE。
- Outbox 必须回收因进程中断遗留的过期 `PROCESSING` 记录。Worker 批次接收必须在 D1 中原子持久化快照和审计记录；失败不得返回成功确认。
- POS 页面保留地址、storeId、上传密钥、检索 HMAC、启用开关和状态；移除 Key ID、人工首次回填开关与批量大小。增加测试连接、立即同步、数据修复和最近失败信息。测试连接只验证签名和 D1 可用性，不写会员快照。
- 不在本切片实现手机查询网页、Cloudflare Access、Telegram、商品查询或任何云端写回功能。

## 验收

1. `store-01-test` 的同一 memberId 不影响 `store-01` 快照。
2. 自动首次回填可中断恢复，完成后重启/重新启用不重复扫描；普通变更与回填交错时以最新 Outbox 版本发送。
3. 显式数据修复重新投递活跃和删除/合并会员，但不改变本地业务数据。
4. 进程中断后的过期 `PROCESSING` 可重试；网络/Worker 故障不影响 POS 交易。
5. Worker 拒绝错误签名、错误 storeId 与重放；批次失败不产生本机错误 `SENT`。
6. 完成受影响回归、隔离 Maven 全量、前端构建、Worker 测试、架构门禁和差异检查；不部署或启用真实同步。

## 完成事实

- POS 配置页面仅保留 Worker 地址、可见的 `storeId`、两把密钥和启用开关；新增无会员写入的测试连接、立即同步和显式数据修复。Key ID、手动首次回填开关和批量大小不再向管理员暴露。
- MariaDB `V1.0.27` 回收过期 `PROCESSING` 任务；`V1.0.28` 补齐旧记录的 `store-01` 默认值；`V1.0.29` 移除本机配置表中已废弃的 Key ID 与首次回填开关列。自动回填保持可恢复；显式修复会覆盖活跃会员及删除/合并会员 TOMBSTONE。
- Worker 改用 `OMNIA_SYNC_STORE_SECRETS_JSON` 的 `storeId -> 密钥（或轮换密钥数组）` 映射，接收快照及审计写入同一 D1 批次。D1 `0002` 移除 `member_snapshots` 对历史 `sync_clients` 的外键，`0003` 移除 nonce/audit 中的旧 Key ID 列。
- Snowflake 会员 ID 与 Outbox 快照版本以精确的十进制文本跨越 Java/Worker 边界；Worker 仅接受正的 SQLite 64 位整数文本（或兼容的小整数 JSON number），并将该文本绑定到 D1，避免 JavaScript 安全整数范围之外的 ID 被截断或拒绝。
- 已通过：隔离 `money_pos_test` 的聚焦回归（含 Snowflake ID）与完整 Maven 回归（188 tests，0 failure/error）、`mvn -q package -DskipTests`、Node 20 Worker `npm test`、两项架构门禁及 `git diff --check`。前端构建沿用先前已通过结果；本次未变更前端。
- 环境验收：2026-10-22 已对 `omnia-cloud-prod` 应用 D1 `0002/0003`，最新部署 Worker 版本 `029a8600-f35d-41af-b836-932ef8e308b8` 至 `https://omnia-cloud.miozen.workers.dev`，并设置 `OMNIA_SYNC_STORE_SECRETS_JSON`。`store-01-test` 合成快照验收返回连接 `200`、批量 `200`、重放 `409`；随后以 `2102313829312065536` 验证精确 Snowflake ID 可被接收并由 D1 `CAST(... AS TEXT)` 原样读回。其后真机 POS 已确认发送 3 条，D1 只读核查为 3 个 ACTIVE 快照、2 个接收批次和 3 个已确认项目；Snowflake ID 由 `CAST(... AS TEXT)` 精确读回。该真机验收使用 `store-01-test` 分区，不影响后续 `store-01` 生产查询分区。
