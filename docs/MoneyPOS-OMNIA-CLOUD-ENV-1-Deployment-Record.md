# MoneyPOS OMNIA-CLOUD-ENV-1 环境部署记录

> 状态：已完成；日期：2026-10-16；适用分支：`dev`。

## 已执行的受授权操作

- 已在 Cloudflare 创建 APAC 位置提示的独立 D1 数据库 `omnia-cloud-prod`，并对其应用
  `0001_member_sync_receiver.sql`；后续检查确认无待应用迁移。
- 已部署仅含内部接收端的 Worker：`https://omnia-cloud.miozen.workers.dev`。未绑定自定义域名，未部署查询网页或 Telegram Webhook。
- 已设置 Worker Secret `OMNIA_SYNC_KEYS_JSON`，并为合成验收初始化一个启用的门店/Key 映射。Secret 值不在仓库、文档或本机保留。
- 使用合成会员数据验证：有效签名请求得到 `200 {"accepted":1}`；完全相同 nonce 的重放得到 `409`；D1 只存储掩码手机号。验收后已清除合成快照、nonce 和审计记录；复核三个业务表计数均为 `0`。

## 当前安全状态

MoneyPOS 本地 `omnia.cloud.sync.enabled` 仍为默认 `false`，没有真实会员数据离开门店。为了避免任何未授权发送，本地一次性验收密钥已经清除；在真实同步前必须轮换 Worker Secret、在门店安全配置中设置相同的签名/检索 HMAC 密钥，并经过单独的生产数据授权。

该 Worker 是公开可访问的 `workers.dev` 地址，但只有签名内部写入路由；当前没有公开查询、网页、Telegram 或自定义域名入口。删除 Worker/D1 是破坏性云端操作，需单独授权。
