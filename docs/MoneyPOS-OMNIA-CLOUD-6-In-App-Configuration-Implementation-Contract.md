# MoneyPOS OMNIA-CLOUD-6 软件内同步配置实施契约

> 状态：已确认，按本切片实施；确认日期：2026-10-21；适用分支：`dev`。

## 目标与边界

Omnia Cloud 的门店同步配置改由后台页面直接维护；不再读取、生成或兼容外部 YAML。单店本地 MariaDB 保存配置，后台读取时不回传两个密钥的实际值。MariaDB 仍是业务权威，Worker/D1 仍只接收查询快照。

## 冻结决定

- 配置项为 Worker 地址、门店编号、租户编号、Key ID、签名密钥、检索 HMAC 密钥、同步启用与首次回填开关。Key ID 相当于门店用户名；两个密钥相当于密码，但协议不使用 Cloudflare 账号密码或 Basic Auth。
- 不保留 `%ProgramData%` YAML、迁移导入或安装后手工文件步骤。当前外部配置加载与示例文件从代码仓库删除。
- 配置读取仍要求 `omniaCloud:view`；保存配置仅要求 `omniaCloud:manage`、超级管理员角色，并且 HTTP 直连来源必须是 loopback。不得信任转发 IP 请求头；局域网/互联网请求即使拥有账号也不能修改。
- 这是单店单客户端配置，使用 ID=1 的本地记录；保存后立即更新运行时同步属性。首次回填和外发仍需要管理员显式打开，默认关闭。

## 验收

验证配置保存后运行时属性更新、GET 不返回密钥、非 loopback 写请求被拒绝、默认不开启同步，并完成隔离 Maven 回归、前端构建、架构门禁和差异检查。真实密钥轮换、Worker Secret 更新和真实会员外发不包含在本切片。

## 完成事实

- `V1.0.26` 新建单例本机配置表，并只为 `SUPER_ADMIN` 初始化配置写权限；默认同步与首次回填均关闭。
- 后台页面直接维护 Worker 地址、门店/租户、Key ID、两个密钥和开关。密钥只接受写入；读取仅返回“是否已配置”。保存立即刷新发布器内存配置，无需重启。
- 写入路由同时要求 `SUPER_ADMIN`、`omniaCloud:manage` 和直连 loopback 地址；不使用 `X-Forwarded-For` 等转发头。读取仍使用原有 `omniaCloud:view`。
- 自动验证已通过：路由契约、隔离 `money_pos_test` 全量 Maven 回归（88 份 Surefire 报告，0 failure/error）、`mvn -q package -DskipTests`、Node 20 `npm run build`、两项架构门禁及 `git diff --check`。前端构建仅保留既有 Browserslist、CSS、动态导入和 chunk-size 警告。
