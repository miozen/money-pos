# GMS-OPT-4：管理路由权限对齐实施契约

现有权限迁移只有 `gmsGoods:*` 与 `gmsBrand:*`，没有独立导入或分析资源。本切片不新增权限、菜单、迁移或角色授权：

- 商品导入使用 `gmsGoods:add`；商品模板与导出使用 `gmsGoods:list`；
- 品牌定价策略读取/保存分别使用 `gmsBrand:list` / `gmsBrand:edit`；
- 周转及库存分析的读取/导出使用 `gmsGoods:list`。

保持所有路由、HTTP 方法、响应、导入/导出数据与认证机制不变；仅把既有 RBAC 权限应用到此前漏标注的 GMS 路由。
