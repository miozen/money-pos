# MoneyPOS OMNIA-CLOUD-2 Worker/D1 接收端实施契约

> 状态：已确认，按本切片实施；确认日期：2026-10-16；适用分支：`dev`。

## 目标与边界

在仓库内提供可独立部署的 `omnia-cloud` Worker/D1 接收端骨架，固定本地 Outbox 的未来安全投递协议。它不属于 MoneyPOS Spring 应用，不能连接 MariaDB，不能写回任何本地业务数据。

本切片只实现 `POST /internal/v1/member-snapshots:batch`。不实现网页、会员查询 API、Telegram Webhook、商品投影、Cloudflare 账号配置、D1 实例创建、密钥设置、网络部署或真实个人信息传输。

## 安全与数据约束

- 每一个门店密钥绑定一个 D1 `sync_clients` 门店；密钥本体仅存 Worker secret `OMNIA_SYNC_KEYS_JSON`，不入库、不入 Git。
- 请求必须有 HMAC-SHA-256、五分钟时间窗、一次性 nonce 和正文 SHA-256。D1 nonce 主键防止重放；版本条件 upsert 使网络重试幂等。
- 接收端拒绝原始 `phone` 字段。后续本地 HTTPS 发布器必须先生成手机全文/后四位的独立 HMAC、脱敏手机号和最小快照；不得把 UMS 当前 DTO 中的原始手机号直接发送到云端。
- D1 保存查询投影和审计计数，不保存本地账本、交易明细、充值/权益历史、地址、备注、操作员或支付信息。删除/合并来源以递增版本 tombstone 覆盖。

## D1 结构与演进

`omnia-cloud/migrations/d1/0001_member_sync_receiver.sql` 创建门店客户端、nonce、会员快照和接收审计表。姓名规范化、手机号 HMAC、后四位 HMAC 均以 `(store_id, 值)` 索引；该设计为后续同权限查询 API 留出索引，但本切片不开放查询。

未来 D1 迁移只能追加，部署时以独立 Worker 项目的受控环境执行；不得以 MoneyPOS Flyway 或门店 MariaDB 迁移替代。

## 验收

本地 Node 测试必须证明合法 HMAC 可验签、正文或时间窗变化会被拒绝、原始手机号不会被接收。D1 实例部署、真实密钥轮换和端到端 HTTPS 同步属于下一次经授权的外部环境验收。
