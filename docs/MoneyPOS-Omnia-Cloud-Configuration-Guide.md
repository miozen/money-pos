# MoneyPOS Omnia Cloud 会员同步配置指南

> 适用范围：单店 MoneyPOS → Cloudflare Worker → D1 的只读会员查询。
>
> 本文是运维说明，不改变收银、退款、库存、会员资产或 MariaDB 的权威地位。云端只保存会员查询快照，不能写回 POS。

## 1. 先理解四个角色

```text
MoneyPOS 本机
  └─ 签名后上传会员快照 ──> Cloudflare Worker ──> D1
                                      ├─ 手机网页查询
                                      └─ Telegram 私聊查询
```

- **MoneyPOS / MariaDB**：唯一业务权威。余额、会员券、寄存商品、金额权益和目标权益都只以本机数据为准。
- **Worker**：验证上传签名、保存 D1 快照，并提供网页和 Telegram 的只读查询。
- **D1**：只保存查询需要的快照；不保存原始手机号、交易流水、支付流水或 POS 密码。
- **网页 / Telegram**：都只读取 Worker 的同一份会员快照，不直接连接 POS 或 MariaDB。

## 2. 最重要的对应关系

同一套同步必须让以下值一致：

| 含义 | POS 后台 | Worker 配置 | 作用 |
| --- | --- | --- | --- |
| 门店分区 | `门店编号` | `OMNIA_QUERY_STORE_ID`，以及签名密钥映射中的键 | 隔离 D1 中不同门店的会员快照；网页和 TG 从此分区读取。 |
| 上传签名密钥 | `签名密钥（密码）` | `OMNIA_SYNC_STORE_SECRETS_JSON` 中该门店的值 | 证明上传请求来自已配置的 POS。 |
| 手机检索 HMAC 密钥 | `检索 HMAC 密钥` | `OMNIA_SEARCH_HMAC_SECRET_BASE64` | 让完整手机号和后四位可在不上传原始手机号的前提下匹配。 |

> POS 的“门店编号”不是另一个账号；它的值必须与 Worker 的查询分区一致。例如正式单店统一使用 `store-01`，测试隔离环境统一使用 `store-01-test`。

## 3. 参数逐项解释

### 3.1 POS 后台参数

在后台进入“Omnia Cloud 会员同步”。配置保存在本机 POS 数据库，只能从本机后台修改。

| POS 字段 | 应填内容 | 是否每台 POS 都要设置 |
| --- | --- | --- |
| Worker 地址 | `https://omnia-cloud.miozen.uk/internal/v1/member-snapshots:batch` | 是。保持为完整批量上传地址。 |
| 门店编号 | 正式使用 `store-01`；测试隔离使用 `store-01-test` | 是，且必须与 Worker 对应。 |
| 签名密钥（密码） | Base64 格式的上传密钥 | 是；必须与 Worker 中该门店的签名密钥完全相同。 |
| 检索 HMAC 密钥 | Base64 格式的检索密钥 | 是；必须与 Worker 的检索 HMAC 密钥完全相同。 |
| 同步开关 | 日常使用时启用 | 是。关闭时不上传，不影响本地收银。 |

页面默认只显示“已设置”。本机超级管理员可点击每个密钥输入框右侧的眼睛主动读回、查看并复制该项；再次点击眼睛、刷新或离开页面后，已显示值会从页面内存清空。普通读取配置和远程请求仍不会得到密钥。

### 3.2 Worker 配置

在 Cloudflare 的 `omnia-cloud` Worker 中配置。不要把这些值写进 Git、代码、截图或网页 JavaScript。

| Worker 名称 | 类型 | 内容与用途 |
| --- | --- | --- |
| `DB` | D1 Binding | 绑定 `omnia-cloud-prod`。这是数据库绑定，不是普通文本变量。 |
| `OMNIA_SYNC_STORE_SECRETS_JSON` | Secret | 门店到上传签名密钥的 JSON 映射。Worker 用它验证 POS 上传。 |
| `OMNIA_SEARCH_HMAC_SECRET_BASE64` | Secret | 与 POS “检索 HMAC 密钥”相同，用于网页和 TG 把输入手机号转换为检索哈希。 |
| `OMNIA_QUERY_STORE_ID` | 文本变量 | 网页和 Telegram 要读取的固定门店分区，例如 `store-01`。 |
| `OMNIA_QUERY_PASSWORD` | Secret | 手机网页查询密码；与 POS 上传无关。 |
| `OMNIA_TELEGRAM_BOT_TOKEN` | Secret | BotFather 给出的 Bot Token；与 POS 上传无关。 |
| `OMNIA_TELEGRAM_WEBHOOK_SECRET` | Secret | Telegram Webhook 验证密钥；与 POS 上传无关。 |
| `OMNIA_TELEGRAM_ALLOWED_USER_ID` | 文本变量 | 允许查询的个人 Telegram 数字 User ID。 |

### 3.3 `OMNIA_QUERY_PASSWORD` 是什么

它只是手机网页的访问密码。用户在网页输入正确密码后，浏览器才可调用查询接口。

- POS 不填写它；它不参与同步签名。
- Telegram 不使用它；Telegram 使用自己的 Bot Token、Webhook Secret 和允许用户 ID。
- 它应只保存在 Worker Secret 中。

## 4. Worker 的签名映射应怎样写

单店正式环境的 `OMNIA_SYNC_STORE_SECRETS_JSON` 示例：

```json
{
  "store-01": "替换为与POS签名密钥完全相同的Base64值"
}
```

若需要暂时保留测试分区和正式分区，可使用：

```json
{
  "store-01-test": "测试POS的Base64签名密钥",
  "store-01": "正式POS的Base64签名密钥"
}
```

这只是 Worker Secret 的值，不需要在 D1 手工插入门店授权，也不需要执行 D1 SQL。

## 5. 首次配置正式 POS 的推荐步骤

以下假设正式分区使用 `store-01`。若仅继续使用已有测试分区，把文中的 `store-01` 改为 `store-01-test`，且三处保持一致即可。

1. 决定门店分区：正式单店使用 `store-01`。
2. 准备两个不同的随机 Base64 值：一个用于上传签名，一个用于手机号检索 HMAC。
3. 在 Worker 中一次性配置：
   - `OMNIA_SYNC_STORE_SECRETS_JSON`：加入 `store-01` 和上传签名密钥；
   - `OMNIA_SEARCH_HMAC_SECRET_BASE64`：填写检索 HMAC 密钥；
   - `OMNIA_QUERY_STORE_ID`：填写 `store-01`。
4. 在实际 POS 后台填写 Worker 地址、`store-01`、同一对密钥，开启同步并保存。
5. 点击“测试连接”。成功提示只代表签名验证和 D1 连通正常，**不会写入会员快照**。
6. 点击“立即同步”。首次启用会自动分批回填历史会员。
7. 查看“本地待发箱”：失败待重试应逐步变为 `0`，已确认发送会增加。
8. 在网页或 Telegram 用一位刚同步的会员分别测试姓名、完整手机号和后四位。

日后正常使用时，不需要反复改 Worker；POS 保持启用，业务变更会进入本地待发箱并由后台调度发送。

## 6. 首次回填、立即同步和数据修复的区别

| 操作 | 做什么 | 何时使用 |
| --- | --- | --- |
| 首次回填 | 第一次启用后，把历史会员分批放入待发箱 | 首次配置。中断后可从检查点继续。 |
| 立即同步 | 立刻发送当前待发箱中的一批数据 | 保存配置后或想快速查看最新结果时。 |
| 数据修复 | 按当前会员状态重新投递全部快照，包含已删除会员 | 修改检索 HMAC、云端漏数据、需要重建云端快照时。 |

三者都不会修改本地会员资产、交易、库存或 MariaDB 数据。

## 7. 忘记签名密钥或检索 HMAC 密钥怎么办

旧 Secret 与 POS 已保存的密钥都不能安全地读取出来。不要猜测旧值，直接成对重置：

1. 生成新的 Base64 上传签名密钥和新的 Base64 检索 HMAC 密钥。
2. Worker 更新：
   - `OMNIA_SYNC_STORE_SECRETS_JSON` 中目标 `storeId` 对应的签名密钥；
   - `OMNIA_SEARCH_HMAC_SECRET_BASE64` 为新的检索 HMAC 密钥。
3. POS 更新为同一对新值，保存并启用。
4. 点击“测试连接”，确认成功。
5. 点击“数据修复”，等待待发箱发送完成。

为什么必须“数据修复”：D1 旧快照里保存的是旧检索 HMAC 计算出的手机号哈希。新密钥启用后，网页和 Telegram 计算出的新哈希无法匹配旧值；重新投递会覆盖为新哈希。

在修复完成前，姓名查询通常仍可用；完整手机号和后四位查询可能暂时无结果。

## 8. 更换密钥时的注意事项

- **签名密钥**：仅影响上传鉴权。Worker 支持为一个门店暂时保存“新旧两个签名密钥”，可用于短暂平滑切换；POS 更新完成后应移除旧值。
- **检索 HMAC 密钥**：同时影响 POS 上传哈希和网页/TG 查询哈希。必须先让 Worker 与 POS 使用相同的新值，再执行“数据修复”。
- 不要把签名密钥、检索 HMAC、网页密码、Bot Token 发到群聊、提交到 Git 或填入普通 Worker 文本变量。

## 9. 常见现象与排查

| 现象 | 最可能原因 | 处理方式 |
| --- | --- | --- |
| 测试连接报 401 / 签名不匹配 | POS 签名密钥与 Worker 映射不一致，或门店编号不存在 | 比对 `storeId`，再同步更新双方签名密钥。 |
| 测试连接成功但没有会员 | 这是正常行为；测试连接不写数据 | 点击“立即同步”或等待调度。 |
| 待发箱有失败待重试 | 网络、Worker 地址、签名或 D1 服务暂时异常 | 先测试连接；恢复后等待重试或点“立即同步”。 |
| 姓名能查到，手机号/后四位查不到 | POS 与 Worker 的检索 HMAC 不同，或 HMAC 刚更换但未修复 | 统一 HMAC 后执行“数据修复”。 |
| 网页/TG 都查不到刚上传的会员 | `OMNIA_QUERY_STORE_ID` 与 POS 门店编号不同，或待发箱尚未确认发送 | 统一分区值，确认 `SENT` 增加后再查。 |
| 网页提示密码错误 | `OMNIA_QUERY_PASSWORD` 与输入密码不同 | 在 Worker Secret 中重设网页密码；POS 无需变更。 |
| Telegram 不回复会员数据 | 不在允许的私人聊天、User ID 不匹配，或 Webhook Secret/Token 配置异常 | 检查 TG 三项专用配置；不影响 POS 上传。 |

## 10. 日常最简操作清单

1. Worker 配好后，不需要每天修改它。
2. POS 保持“同步开关”为启用。
3. 偶尔查看待发箱：失败待重试为 `0`、已确认发送持续增加即可。
4. 只有云端结果明显缺失、重置检索 HMAC 或迁移云端数据时，才使用“数据修复”。
5. 本地 POS 离线、Worker 故障或 D1 故障时，收银继续以本机 MariaDB 为准；云端查询只会延迟更新。

## 11. 当前实现边界

- Worker 接收接口：`POST /internal/v1/member-snapshots:batch`。
- 连接测试接口：`POST /internal/v1/member-snapshots:test`。
- 手机网页与 Telegram 都固定读取 `OMNIA_QUERY_STORE_ID` 指向的分区。
- 查询结果只返回展示快照，不会暴露手机号 HMAC、原始手机号、POS 密码或数据库内部字段。
- D1 是查询缓存，不是第二套 POS；任何云端配置都不能远程修改本地会员资产。

实现依据可见于：

- `omnia-cloud/apps/worker/src/index.js`
- `omnia-cloud/apps/worker/README.md`
- `money-pos-web/src/views/system/omniaCloud/index.vue`
- `money-pos/qk-money-app/money-app-biz/src/main/java/com/money/feature/ums/application/cloudsync/`
