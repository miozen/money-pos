# UMS-OPT-1：会员资金流水权限对齐实施契约

`GET /ums/member-log` 输出会员余额、券与充值/撤销审计流水。该入口复用现有会员档案读取权限
`umsMember:list`，不新增权限编码、菜单、迁移或角色授权，不改变路由、查询筛选或响应。

验收：路由保留原 GET/响应契约，且反射路由契约固定其 `@PreAuthorize("@rbac.hasPermission('umsMember:list')")`。
