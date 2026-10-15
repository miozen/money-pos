# TRADE-OPT-1：订单重打权限对齐实施契约

`GET /oms-order/hardware/print` 是读取订单后触发真实打印机的物理副作用入口。它复用既有订单读取权限
`oms:order:list`；不新增权限、菜单、迁移或角色授权，不改 GET 路径、打印内容、钱箱参数或订单查询。

验收以路由反射契约固定 GET 路径与 `@PreAuthorize("@rbac.hasPermission('oms:order:list')")`。
