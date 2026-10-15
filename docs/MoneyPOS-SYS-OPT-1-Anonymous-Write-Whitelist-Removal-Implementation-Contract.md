# SYS-OPT-1：撤销匿名写白名单实施契约

客显设备继续可以匿名读取 `GET /common/display-settings`，但匿名主体不得上传文件或写入客显配置。

本切片仅从 `money.security.ignore` 删除 `POST /common/upload` 与 `PUT /common/display-settings`；安全链会将这两条
既有写路由恢复到默认认证要求。路由、控制器、请求/响应、文件格式、客显 JSON、策略、打印、备份、迁移、权限码和
角色授权均不改变。

验收以配置契约测试固定：读取仍在 GET 忽略列表，上传与客显写入不再位于 POST/PUT 忽略列表。再执行隔离
`money_pos_test` 全量回归、打包、架构门禁和差异空白检查。
