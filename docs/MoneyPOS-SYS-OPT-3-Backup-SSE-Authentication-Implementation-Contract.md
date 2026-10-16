# MoneyPOS SYS-OPT-3：备份 SSE 认证兼容实施契约

> 状态：**已确认，待实施**；确认日期：2026-10-16；适用分支：`dev`。

## 目标

将备份控制台 SSE 从任何匿名连接均可订阅，收敛为仅既有 `SUPER_ADMIN` 可建立的连接，同时保持 Electron、浏览器和
现有 `EventSource` 的运行时基址兼容。

## 已确认事实

1. 备份导出和还原已由 `SUPER_ADMIN` 保护；`GET /sys/backup/stream` 因浏览器原生 `EventSource` 不能携带现有
   Bearer 请求头而被安全链匿名放行。
2. 前端 token 存于 `sessionStorage`，Axios 会添加 `Authorization`，但 EventSource 不经过 Axios 拦截器。
3. 不能把长期 Bearer JWT 放入 URL；这会扩大浏览器历史、代理日志和异常日志中的凭据泄露面。

## 冻结方案

1. 新增受 `SUPER_ADMIN` 保护的 `POST /sys/backup/stream-ticket`，经普通 Axios Bearer 认证取得短时、一次性、
   仅内存保存的随机票据。
2. 保留 `GET /sys/backup/stream` 的匿名传输通道，但它必须携带 `ticket` 查询参数；服务端原子消费票据后才创建
   `SseEmitter`。缺失、过期或重复票据一律拒绝。
3. 票据不映射为 JWT、不写数据库、不写备份内容、不改变导出/还原、日志 payload、SSE 事件格式、运行时基址、
   角色模型或自动备份广播规则。
4. 前端仅将一次性票据编码进即时 EventSource URL；不得将长期 access token 放入 URL、localStorage 或日志。

## 验收

- 票据仅能由 `SUPER_ADMIN` 路由取得；一次消费、过期和未知票据均不可建立流。
- 前端继续使用 `getApiBaseUrl()`，Electron `file://` 包与浏览器均可连接。
- 既有导出/还原权限契约和 SSE JSON payload 回归通过。
- 不新增权限码、菜单、迁移、跨 Feature 依赖或公开的长期凭据传递。
