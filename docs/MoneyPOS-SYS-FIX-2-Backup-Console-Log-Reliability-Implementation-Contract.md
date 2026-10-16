# SYS-FIX-2：备份控制台日志可靠性实施契约

## 已确认问题

`/sys/backup/stream` 使用的 SSE 基址与 Axios 的 Electron `file://` 运行时覆盖逻辑不一致：Axios 会连接本地 Java
服务，SSE 却仍使用构建时相对路径，导致打包版控制台无法建立通道。服务端 `sendLog` 手工拼接 JSON，进程或校验错误中的
引号、反斜杠或换行会生成无效事件，而前端会静默忽略解析失败。`createBackupZip` 本身没有发布阶段日志，因此一键导出只显示
下载提示，控制台不能反映实际进度。

## 最小变更

1. 前端 SSE 复用 Axios 已解析的 API 基址，保证开发、反向代理和 Electron `file://` 运行时指向同一后端；不为
   `EventSource` 增加认证头，也不改变 `/sys/backup/stream` 的既有匿名兼容策略。
2. 服务端将日志事件序列化为 JSON，而非格式化字符串拼接，保持前端 `{time, level, msg}` 数据形状。
3. 为手动备份及还原中的前置保护快照发布开始、数据库导出、静态资源归档、ZIP 打包、成功或失败等阶段日志。定时
   `AutoBackup_` 不向正在打开的人工控制台额外广播，避免改变日常界面语义。

## 不在范围内

- 不改变导出、还原或 SSE 路径、HTTP 响应、ZIP/Manifest 格式、影子库切换、静态资源回滚、权限和
  `SUPER_ADMIN` 决策。
- 不改变 SSE 认证方案、Electron 安全模型或后台任务的备份行为。
- 不新增迁移、菜单、权限码或跨 Feature 依赖。

## 验收

- 服务端回归证明包含引号和换行的日志消息仍能作为可解析 JSON SSE payload 广播。
- 前端 SSE URL 复用 Axios 的运行时基址，且不会将 `http://` 错误压缩为 `http:/`。
- 执行受影响测试、隔离 `money_pos_test` 全量 Maven 回归、打包、两项架构门禁与差异空白检查。
