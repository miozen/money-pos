# MoneyPOS OMNIA-CLOUD-9 Telegram 只读会员查询实施契约

> 状态：已接受，实施中；确认日期：2026-10-26；适用分支：`dev`。

## 目标与边界

在已部署的 OMNIA-CLOUD-8 网页查询之上，为唯一指定的 Telegram 私聊账号增加只读会员查询。MariaDB 仍为唯一权威数据源；Worker/D1 只读取已有会员快照。不得改变 POS、同步、D1 schema、网页认证或任何收银/资产规则。

## 冻结决定

- Worker 增加唯一入口 `POST /telegram/webhook`，由 Telegram `setWebhook` 指向既有 `workers.dev` 地址；不提供浏览器查询或任意 Telegram API 转发入口。
- `OMNIA_TELEGRAM_BOT_TOKEN` 与 `OMNIA_TELEGRAM_WEBHOOK_SECRET` 是 Worker Secret；`OMNIA_TELEGRAM_ALLOWED_USER_ID` 是唯一允许访问者的数值变量。Token、Secret 和用户 ID 不进入 Git、D1、POS 或响应。
- 每个请求必须同时通过 Telegram 的 `X-Telegram-Bot-Api-Secret-Token` 校验、指定 User ID 校验和 `private` chat 校验。未授权、群组、频道或不支持更新只获得普通 Webhook 成功确认，绝不读取 D1 或发送会员内容。
- `/start`、`/help` 返回使用说明；直接发送姓名、完整 11 位手机号或后四位即查询。查询规则、固定 `OMNIA_QUERY_STORE_ID` 分区、ACTIVE 过滤、20 条上限、手机号 HMAC 和展示投影复用 OMNIA-CLOUD-8。
- 单条匹配直接返回详情；多条匹配仅显示姓名和掩码手机号的内联选择按钮，回调仍须再次校验私聊和 User ID，且只读取固定分区 ACTIVE 快照。
- Telegram 文本采用与网页一致的会员余额、会员券、剩余满减券、品牌等级、三类权益和最后同步时间术语；不返回原始手机号、检索 HMAC、规范化姓名、会员编号、交易、操作员或内部 D1 字段。
- 不保存 Telegram 会话、消息、搜索历史或额外 D1 数据；Telegram API 失败让 Telegram 重试，不影响网页、同步或 POS。

## 验收

1. 缺失配置或错误 Webhook Secret 不能读取会员数据；错误 Secret 返回 401。
2. 非允许 User ID、群组和频道不读 D1、不发送会员消息。
3. 指定私聊账号可用姓名、完整手机号、后四位查询；多结果选择只能读取固定分区的 ACTIVE 快照。
4. Telegram 详情术语和值与网页投影一致，且不含敏感检索字段或会员编号。
5. Worker Node 测试、既有打包/架构门禁和差异检查通过；云端设置 Secret、设置 Webhook 和真实 Telegram 验收另经授权执行。

## 本地完成事实

- Worker 已实现受 Telegram Secret、固定 User ID 和私聊类型三重限制的 Webhook，并复用既有查询条件、固定分区和 ACTIVE 快照投影；未新增 D1 migration、POS 改动或同步写入。
- Worker Node 测试覆盖有效私聊查询、展示术语、错误 Webhook Secret、非允许用户、群聊忽略、多结果内联选择及回调详情读取。
- 已通过 Worker `npm test`、`mvn -q package -DskipTests`、两项架构门禁及 `git diff --check`。部署、Secret 设置、`setWebhook` 和真实 Telegram 验收尚未执行。
