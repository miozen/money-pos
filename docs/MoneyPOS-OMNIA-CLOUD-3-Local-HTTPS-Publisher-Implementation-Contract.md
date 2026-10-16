# MoneyPOS OMNIA-CLOUD-3 本地 HTTPS 发布器实施契约

> 状态：已确认，按本切片实施；确认日期：2026-10-16；适用分支：`dev`。

## 目标与边界

将已完成的 UMS Outbox 接至 OMNIA-CLOUD-2 的内部批量接收协议。发布在本地定时任务之外执行，成功才确认 Outbox；失败统一走既有退避。MariaDB 始终为权威数据，Worker/D1 不参与交易或资产写入。

本切片不创建 Cloudflare 帐号、D1、Worker secret 或部署，不配置真实端点/密钥，不开放查询或 Telegram，也不改变收银、退款、库存和会员资产事务。

## 协议与隐私

- `omnia.cloud.sync.enabled` 默认 `false`；只有显式启用且提供 HTTPS endpoint、store id、key id、签名 Base64 密钥和检索 HMAC Base64 密钥时才尝试发网。
- 一次最多发布 50 条已领取 Outbox。批次成功后逐条确认；任何失败会使整批按既有规则退避，重复发由 Worker 版本 upsert 消化。
- 线上 JSON 永不包含 `phone`。发布器仅生成标准化姓名、完整手机号 HMAC、后四位 HMAC 和掩码手机号；业务资产投影沿用 OMNIA-CLOUD-1 的最小快照边界。TOMBSTONE 只发身份、版本、操作和时间。
- 签名覆盖方法、路径、时间、nonce 和正文 SHA-256，与 Worker 约定一致。

## 验收与后续

专项测试验证原始手机号不会进入请求、TOMBSTONE 不携带投影、签名/Hash headers 完整。真实 Cloudflare 环境、密钥轮换、D1 迁移执行及端到端 HTTPS 验收须在用户提供授权和环境后另行执行。
