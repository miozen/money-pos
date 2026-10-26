# MoneyPOS OMNIA-CLOUD-11 本机密钥显示实施契约

> 状态：已接受，实施中；确认日期：2026-10-26；适用分支：`dev`。

## 目标与边界

让本机超级管理员在 Omnia Cloud 设置页按需查看、复制已保存的上传签名密钥和检索 HMAC 密钥，避免遗失后不必要的 Worker/POS 双端重置与全量修复。

不改变密钥存储、同步协议、Worker、D1、会员数据、网页查询、Telegram、收银或远程禁止修改的规则。

## 冻结决定

- 页面默认不读取、不显示密钥，仍只显示“已设置”。
- 新增单独的本机 `POST` 显示接口；它必须同时要求 `SUPER_ADMIN`、`omniaCloud:manage` 和 loopback 请求，与保存/修复/立即同步使用相同边界。
- 普通 `GET /config`、状态接口和任何远程请求继续不返回密钥。
- 管理员点击带眼睛图标的“显示已保存密钥”后才请求值并填入现有密码输入框；输入框现有的显示/隐藏眼睛与复制能力继续可用。
- 点击“隐藏已保存密钥”、刷新或离开页面时只从浏览器内存清空已显示值；不修改数据库配置。

## 验收

1. 默认加载配置时，两个密钥字段为空且仅显示已设置状态。
2. 本机超级管理员点击显示后，两个密钥可见、可复制且可再次隐藏。
3. 普通配置读取不泄露密钥；显示接口具有管理权限和 loopback 保护。
4. 保存空密钥字段仍保持原有密钥；同步、数据修复、Worker 与 D1 行为不变。

## 本地实施与验证事实

- 新增独立的 `POST /ums/member-cloud-sync/config/reveal-secrets`；它复用本机 loopback、`SUPER_ADMIN` 与 `omniaCloud:manage` 边界，普通 `GET /config` 仍只返回密钥是否已设置。
- 设置页默认不持有密钥；带眼睛图标的按钮按需读取并填入现有密码输入框，点击“隐藏”只清空页面内存。
- 已通过：NVM Node `v22.23.4` 下 `npm run build`、聚焦 `MemberCloudSyncConfigurationServiceTest` 与 `UmsMemberCloudSyncRouteContractTest`、`mvn -q package -DskipTests`、两项架构门禁和 `git diff --check`。前端构建仅有既有 Browserslist、CSS 解析、动态导入与大包体提示。
