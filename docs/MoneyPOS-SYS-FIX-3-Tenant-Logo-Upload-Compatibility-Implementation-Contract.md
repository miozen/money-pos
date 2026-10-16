# SYS-FIX-3：租户 Logo 上传兼容性实施契约

## 已确认问题

租户前端在选择图片后将预览值设为浏览器 `blob:` URL，但全局 `getOssUrl` 仅排除包含 `http` 的地址，错误地把该
本地预览拼接为静态资源路径。Windows Electron `file://` 包中，Axios 已改为连接本地 Java 服务，而相对 Logo 路径仍按
构建时 `VITE_BASE_URL` 拼接，因而已保存的租户 Logo 也无法稳定显示。后端 Controller、`tenant` JSON part、`logo`
文件 part、`SysTenantServiceImpl` 的本地 `tenant/` 存储调用均已存在；编译产物保留 `logo` 参数名，但将其显式标注为
`@RequestPart("logo")` 可冻结前后端 multipart 名称。

## 最小变更

1. 提取 Axios 已使用的运行时 API 基址，供 Axios 与资源 URL 解析共同使用；Electron 仍指向
   `http://127.0.0.1:9101/money-pos`。
2. `getOssUrl` 保持 `http(s):`、`blob:` 和 `data:` URL 原样返回；仅对数据库保存的相对路径拼接该运行时基址与
   既有 `/assets/` 前缀。
3. 租户新增、修改的文件参数显式声明为 `logo`，并以 MVC 回归固定 JSON `tenant` part 与图片 `logo` part 的绑定。

## 不在范围内

- 不改变 `/tenants` 路由、请求字段、响应、权限、租户数据、Logo 存储目录、文件格式、删除时机或多租户规则。
- 不修改 GMS 品牌、商品等其他业务的存储逻辑；共享 URL 解析的兼容性改正仅使其现有相对资源在 Electron 中正确访问。
- 不新增迁移、菜单、权限码或跨 Feature 依赖。

## 验收

- MVC 回归证明 multipart 请求中的 `tenant` JSON part 和 `logo` 图片 part 均正确传入租户服务。
- 前端生产构建通过；静态检查确认 `blob:` 不再被拼接为 `/assets/blob:`，Electron 运行时资源使用同一后端基址。
- 执行受影响测试、隔离 `money_pos_test` 全量 Maven 回归、打包、两项架构门禁与差异空白检查。
