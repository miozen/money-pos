# MoneyPOS OMNIA-CLOUD-8 只读会员查询实施契约

> 状态：实现已提交，待部署；确认日期：2026-10-22；适用分支：`dev`。

## 目标与边界

在已验收的 OMNIA-CLOUD-7 会员快照接收端上，为本人提供手机优先的只读会员查询页面。MariaDB 仍是唯一权威数据源；Worker/D1 只读取已同步快照，绝不接入收银、退款、库存或任何本机写入。

本切片不实现 Telegram、商品查询、自有用户体系、Cloudflare Access、多门店管理、POS 配置改动或 D1 schema 迁移。

## 冻结决定

- 查询分区由 Worker 环境变量 `OMNIA_QUERY_STORE_ID` 固定；浏览器请求不能指定 `storeId`。生产应设为 `store-01`；真机验收可临时设为 `store-01-test`，两者在既有复合主键中隔离。
- 网页和 API 以 Worker secret `OMNIA_QUERY_PASSWORD` 的单一密码保护；浏览器仅经 HTTPS 将其用于同源 Basic Authorization，不在源代码、D1、POS 或响应中保存。页面本身不含会员数据；所有查询/详情 API 均强制鉴权。
- 手机号码检索 HMAC 继续使用 Worker secret `OMNIA_SEARCH_HMAC_SECRET_BASE64`，其值必须与 POS 的“检索 HMAC 密钥”一致，且绝不返回给浏览器。完整手机号以全文 HMAC 精确匹配；四位数字以末四位 HMAC 精确匹配。
- 姓名按同步端相同的 `trim + lower-case` 规则作前缀匹配；所有结果仅返回 ACTIVE 快照，最多 20 条。查询 API 不接受通用 SQL、任意分区或已删除会员。
- Worker 只对外返回展示投影：姓名、掩码手机号、会员编号、余额、优惠余额、品牌等级、可用券、QUANTITY/AMOUNT/TARGET 权益及最后同步时间。不得返回检索 HMAC、标准化姓名、原始手机号、交易/账本/操作员字段或内部 D1 列。
- 页面使用无依赖的同源静态 HTML/CSS/JS，以避免为一个单人查询页引入单独构建、发布与依赖维护；它和未来 Telegram 均复用 Worker 内的查询/展示投影函数。

## API

- `GET /`：返回手机优先的查询页，不包含快照数据。
- `POST /api/v1/members/search`：受密码保护；正文为 `{ "query": "..." }`。11 位手机号按完整手机号精确匹配，4 位数字按后四位匹配，其他非空文本按姓名检索。
- `GET /api/v1/members/{memberId}`：受密码保护；只读取固定分区的 ACTIVE 快照详情。会员 ID 必须是正的 SQLite 64 位整数文本。
- 所有 API 响应 `Cache-Control: no-store`；鉴权失败返回 401，未找到返回空数组或 404，配置缺失返回 503，错误输入返回 400。

## 验收

1. 无密码、错误密码、未配置密码均不能读取任何会员数据。
2. 姓名、完整手机号、后四位均只能读取固定分区的 ACTIVE 快照；删除快照不能出现。
3. 手机号 HMAC 在 Worker 内生成，响应不包含检索 HMAC 或原始手机号。
4. 大 Snowflake member ID 可精确读取详情；结果上限为 20。
5. Worker Node 测试、现有 Maven/架构门禁与差异检查通过；部署、Worker secrets 设置和真实手机验收另经环境授权执行。

## 本地完成事实

- Worker 已实现固定分区的查询/详情 API 与同源手机页面；不新增 D1 迁移、POS 改动或任何云端写接口。
- Node Worker 测试覆盖错误密码、固定查询分区、完整手机号 HMAC、ACTIVE 过滤、检索字段不泄露和大 Snowflake ID 详情读取。
- 已通过隔离 `money_pos_test` 完整 Maven 回归（188 tests，0 failure/error）、`mvn -q package -DskipTests`、两项架构门禁、Worker `npm test` 及 `git diff --check`。本切片尚未部署，未设置查询密码/检索 HMAC/查询分区，也未进行手机查询验收。
- 实现提交为 `2680f8c`（`feat(cloud): add read-only member query`）并已推送；立即 fetch 后 `dev...origin/dev = 0/0`。
- 2026-10-23 的受权环境操作未改变 Cloudflare：本机 Wrangler 刷新登录令牌访问 `https://dash.cloudflare.com/oauth2/token` 时发生 TLS `ECONNRESET`，在首次 Secret 写入前终止。因而 `OMNIA_QUERY_PASSWORD`、`OMNIA_SEARCH_HMAC_SECRET_BASE64`、`OMNIA_QUERY_STORE_ID` 和新版 Worker 均未写入/部署；公网 Worker 保持旧版接收端状态。
