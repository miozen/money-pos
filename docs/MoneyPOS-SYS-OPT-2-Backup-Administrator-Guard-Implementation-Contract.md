# SYS-OPT-2：备份导出与还原超级管理员保护实施契约

## 已确认业务规则

SYS 的策略、打印、客显等日常配置继续采用“已认证用户即可操作”的简化模型；不新增细粒度权限码、菜单、迁移或角色授权。

备份导出与备份还原属于系统级灾备操作，仅既有 `SUPER_ADMIN` 角色可以执行。

## 最小变更

仅在 `SysBackupController` 的既有路由上增加现有 RBAC 角色表达式：

| 路由 | 授权 |
| --- | --- |
| `GET /sys/backup/export` | `@rbac.hasRole('SUPER_ADMIN')` |
| `POST /sys/backup/restore` | `@rbac.hasRole('SUPER_ADMIN')` |

沿用 `RbacAuthorizationService.hasRole` 与现有 `SUPER_ADMIN` 角色码；不得新建权限资源或授权映射。

本切片不改变备份 ZIP 内容、导出流、还原事务、错误响应、运行时数据库、SSE 日志路由或其现有匿名连接方式。`GET /sys/backup/stream`
是否也需要认证，须在确认 Electron/SSE 的认证传输兼容性后另行决策，不能在本切片隐式改变。

## 验收

以反射路由契约测试固定两个路由、HTTP 方法与 `SUPER_ADMIN` 表达式；再执行隔离 `money_pos_test` 全量 Maven 回归、打包、架构门禁与差异空白检查。
