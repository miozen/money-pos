# FIN-OPT-1：瀑布日报权限对齐实施契约

`GET /finance/report/waterfall/daily` 输出收入、退款、采购与利润的全口径日结数据。该入口复用其余 FIN 路由使用的
`omsOrder:list` 权限，不新增权限、菜单、迁移或角色授权，不改变查询参数、响应、公式或上游读模型。

验收以路由反射契约固定 GET 路径与 `@PreAuthorize("@rbac.hasPermission('omsOrder:list')")`。
