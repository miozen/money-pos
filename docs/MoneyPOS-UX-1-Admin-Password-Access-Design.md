# MoneyPOS UX-1.1：后台独立认证最终设计

> 审查与实施日期：2026-09-23；源码基线：`dev` / `372a25d`。UX-1.1 已按本文实施；不修改业务表或数据库迁移。

## 结论与范围

UX-1.1 要解决的是 POS 收银身份和后台身份复用：POS 已由收银员 A 登录时，打开后台的人必须以自己的 MoneyPOS 账号和密码登录；后台可为店长 B，POS 继续是 A。后台登录后的菜单与 API 权限完全复用现有 `User → Role → Permission` RBAC。

推荐方案是由 Electron 主进程维护一个唯一的后台窗口。该窗口使用一次性、非持久的独立 session partition，固定从登录页开始，关闭窗口即结束本次后台会话。UX-1.1 不创建共享后台密码、独立账号体系或 `isAdmin` 特判；不修改收银、退款、库存、会员资产、订单审计、表/Flyway，也不处理默认密码和改密会话失效。

## 源码事实与根因

| 范围 | 已确认事实 | 结论 |
| --- | --- | --- |
| POS 后台入口 | `views/pos/index.vue` 对 `admin` 直接执行 `window.open(router.resolve({ path: '/' }).href, '_blank')`。 | 没有后台重认证步骤。 |
| Electron | `main.cjs` 仅创建主 POS 和客显窗口；均未设置 `partition` 或 `preload`。 | 现有窗口使用默认 Electron session，不能作为认证隔离边界。 |
| 前端状态 | token 在 `sessionStorage.accessToken`；Pinia user/app state 是单 renderer 内存；用户名提示使用 `localStorage`。 | Pinia 不跨 BrowserWindow 共享，但 `window.open` 的 opener/同源存储行为不能作为安全保证。 |
| 路由 | 有 token 时访问 `/login` 会跳转 `/pos`，并加载 `/auth/router` 动态菜单。 | Admin 必须在无 POS token 的隔离 session 中打开，登录页须区分后台入口。 |
| 登录 | `POST /auth/login` 的 `LoginDTO` 固定为 `username`、`password`；服务端 BCrypt 校验密码与 `enabled`，成功后签发 token。 | 下拉选择后仍提交 username，不修改既有认证协议。 |
| 用户 | `SysUser` 有 username、nickname、enabled、tenantId；没有逻辑删除，删除为物理删除；`initLogin` 无生产使用点。 | 候选账号为当前默认租户中 `enabled=true` 的用户，不按角色或 initLogin 过滤。 |
| 用户管理 API | `GET /users` 要求 `user:list`，且返回电话、邮箱、角色等完整数据。 | 不能匿名复用或放开该接口。 |
| RBAC | 非白名单 API 默认需认证；动态菜单和业务接口均以现有角色/权限控制。 | 后台登录后可原样复用 RBAC；普通收银员也可登录，但只能使用其被授权能力。 |

根因并非登录接口跳过密码：POS 页面打开应用根路径时带入已有收银认证上下文，前端守卫于是认为后台已登录。

## 产品规则

1. POS 身份与 Admin 身份独立。后台登录、退出、失败、过期和关闭不得修改 POS token、购物车、会员、挂单、路由或订单 `create_by`。
2. 后台首次打开必定显示账号下拉与密码输入；不记住密码、不自动登录、不复用 POS 或历史后台 token。
3. 所有有效系统账号都能认证后台；后台可见菜单与 API 权限继续由原有 RBAC 决定。
4. 最小化、失焦和 POS/Admin 切换保持后台登录；真正关闭后台窗口后结束后台会话，重开必须再次登录。
5. 每个 Electron 应用实例最多一个后台窗口；重复点击只恢复并聚焦该窗口，不允许多个后台身份并存。

## POS/Admin 身份模型与 Electron 生命周期

```text
Main POS BrowserWindow                    唯一 Admin BrowserWindow
default session partition                  unique non-persistent partition
POS token / POS Pinia                      Admin token / Admin Pinia
收银员 A                                  登录页 → 店长 B
       │                                           │
       └── allow-listed IPC 请求 ─► 主进程创建/聚焦 ┘
```

1. 主进程保存 `adminWindow` 引用。POS 不再直接 `window.open()`。
2. POS 请求打开后台：若 adminWindow 存在，先 `restore()`（如最小化）再 `focus()`；不存在才创建。
3. 新窗口使用 `nodeIntegration: false`、`contextIsolation: true`，并为本次窗口设置唯一、没有 `persist:` 前缀的 partition，例如 `money-admin-<随机标识>`。这使它不读取 POS 的 cookie、storage 或缓存，也不会在应用重启后留下后台会话。
4. 窗口只加载 `#/login?entry=admin`。不从 POS 传 token、用户资料、路由或任意 redirect。
5. 在窗口真正销毁前显式清理其 `webContents.session` 的 storage 数据，再销毁窗口并在 `closed` 时清空 `adminWindow` 引用。非持久 partition 不会跨应用重启落盘；显式清理保证同一应用进程内关闭后重开也没有残留认证状态。
6. 新增最小 preload/contextBridge，仅暴露 `openAdminWindow()`；不暴露通用 IPC、Node 或任意新窗口能力。
7. `entry=admin` 是固定枚举：后台登录成功后跳 `/dashboard`；普通登录继续跳 `/pos`。不接受任意 URL 作为 redirect。

## 前端认证状态隔离

| 状态 | POS 窗口 | Admin 窗口 | 约束 |
| --- | --- | --- | --- |
| `sessionStorage.accessToken` | 收银 token | 后台 token | 独立 partition + renderer，不互读互写。 |
| Pinia user/app | A 的用户与菜单 | 实际后台用户与菜单 | 各 renderer 独立；禁止 IPC 同步。 |
| `localStorage` 用户名提示 | 保留原 POS 行为 | 不作为 Admin 预填或密码记忆来源 | Admin 仅使用候选下拉选择账号。 |
| 登录后路由 | `/pos` | `/dashboard` | 只由白名单 entry 决定。 |

Admin Axios、401 和 logout 在自己的 renderer 中读写 token；不得使用 `window.opener`、BroadcastChannel、共享 localStorage 或 IPC 影响 POS。

## 后端认证与账号候选列表

### 认证复用

Admin 提交现有 `POST /auth/login`，body 保持 `{ username, password }`。无需 userId 登录、后台密码、新 token 类型或新 RBAC。

### 最小候选端点

账号下拉需要未认证数据，但不能开放 `/users`。新增精确的匿名只读端点 `GET /auth/login-candidates`，仅返回：

```json
[{ "username": "stable-login-name", "displayName": "昵称或用户名" }]
```

规则：

1. 仅查询 `enabled=true` 的 `sys_user`；物理删除用户自然排除；按 displayName、username 稳定排序。
2. 强制使用桌面默认租户；拒绝请求通过 `Y-tenant` 指定其他租户。现有桌面登录没有 tenant header，二者保持一致。
3. 不返回 id、密码/hash、电话、邮箱、备注、头像、最后登录时间、角色、权限或完整用户对象；角色名称不是认证所必需信息。
4. 前端选择 username 后直接调用原登录接口，候选列表不是认证凭据。
5. 端点必须要求 loopback 来源（127.0.0.1 或 IPv6 loopback），非 loopback 统一拒绝；设置 `Cache-Control: no-store`。当前后端未显式绑定 loopback，不能仅相信 Electron、Origin 或 CORS 来保护匿名账号枚举。
6. 仅将这一个精确 GET 路径加入安全 ignore 清单；禁止放开 `/users`、`/auth/**` 或任何泛化用户查询。加载失败时不回退完整用户列表或缓存，Admin 留在登录页并允许重试。

## RBAC 与 token 生命周期

后台成功登录后，`/auth/router` 和 API `@PreAuthorize` 使用后台实际用户的普通 token。账号出现在下拉不代表拥有全部后台能力；这是既有 RBAC 的正常语义。

Admin 主动退出时调用现有 `/auth/logout`，只删除 Admin token 与该 renderer 的 Pinia 状态，留在 Admin 登录页。Admin 401/过期同样只影响 Admin。关闭 Admin 窗口时清理并销毁临时 session，不注销 POS token。

当前 JWT 有长期有效期且本地 token 策略不提供可靠撤销；UX-1.1 不把窗口关闭表述为通用 token 撤销。默认/新增账号密码与改密撤销分别登记为 `SEC-1`、`SEC-2`，不混入本切片。

## 异常场景

| 场景 | 结果 |
| --- | --- |
| POS A 打开后台，店长 B 成功登录 | Admin 为 B，POS 为 A；审计各归实际认证用户。 |
| B 密码错误或账号已停用 | Admin 留在登录页并显示统一失败；POS 不变。 |
| 普通收银员登录 Admin | 可以认证；仅有其既有菜单/API 权限。 |
| Admin 无权限、401、主动退出 | 仅 Admin 被拒绝/清理/回登录页；POS 不变。 |
| Admin 最小化或切换到 POS | Admin 登录和页面状态保持。 |
| Admin 关闭、随后重开 | POS 不变；新 Admin 必须重新登录。 |
| 重复点击后台管理 | 不创建第二窗口，恢复并聚焦已有窗口。 |
| 候选列表失败 | Admin 不显示缓存账号，留在登录页；POS 不变。 |

## 预计修改文件

| 文件 | 修改原因 |
| --- | --- |
| `money-pos-web/main.cjs` | 创建、聚焦、关闭唯一 Admin BrowserWindow，配置临时独立 partition。 |
| `money-pos-web/preload.cjs`（新增） | 通过 contextBridge 仅公开打开后台窗口的窄 IPC。 |
| `money-pos-web/src/views/pos/index.vue` | 将直接 window.open 替换为受控 bridge 调用。 |
| `money-pos-web/src/views/Login.vue` | Admin 入口导航、账号下拉、候选加载和失败状态；普通 POS 登录保持不变。 |
| `money-pos-web/src/api/system/auth.js` | 增加候选查询客户端封装。 |
| `money-pos/qk-money-app/money-app-system/src/main/java/com/money/controller/SysAuthController.java` | 增加候选入口及 loopback/tenant 守卫。 |
| `money-pos/qk-money-app/money-app-system/src/main/java/com/money/service/SysAuthService.java` 与实现 | 声明并协调认证页候选查询；不承担用户持久化查询。 |
| `money-pos/qk-money-app/money-app-system/src/main/java/com/money/service/SysUserService.java` 与实现 | 在用户所有者内查询最小字段、启用状态和稳定排序。 |
| `money-pos/qk-money-app/money-app-system/src/main/java/com/money/vo/LoginCandidateVO.java`（新增） | 避免复用并泄露完整 SysUserVO。 |
| `money-pos/qk-money-app/money-app-biz/src/main/resources/application-money.yml` | 仅白名单精确 GET 候选路径。 |
| 认证集成测试与既有回归入口 | 锁定候选最小化、认证失败和 POS 不受影响行为。 |

实施前需确认新增 preload 会随 Electron 打包进入产物；当前 electron-builder 未设置 restrictive `files` 清单，预期项目根目录的 `.cjs` 会被包含，但必须以 Windows EXE 验收而非假设。项目当前没有前端/Electron 自动化测试框架；UX-1.1 不新增该框架，Electron 生命周期以可审查的主进程实现、前端 `npm run build` 和 Windows 实机回归验证。

## 数据与业务影响

| 项目 | 结论 |
| --- | --- |
| 数据库/Flyway | 不需要；使用现有 sys_user 与登录协议。 |
| 收银、退款、库存、会员资产 | 不修改，不进入其事务。 |
| 订单审计 | 不修改；Admin/POS token 分离后各自使用实际认证用户。 |
| User/Role/Permission | 不改变语义，复用既有认证、菜单和授权。 |
| 新匿名面 | 仅 loopback、无缓存、最小字段候选查询。 |

## 回归矩阵

| 场景 | 验证点 |
| --- | --- |
| POS 已登录 → 打开后台 | Admin 必须无 token 并显示登录；POS 收银员、购物车、会员、挂单不变。 |
| 不同账号后台登录 | 正确密码可登录；菜单/API 仅反映该账号权限。 |
| 错误密码、停用账号 | Admin 不进入后台；POS 不变。 |
| 候选账号 | 仅默认租户启用用户的 username/displayName；无敏感字段；非 loopback 被拒绝。 |
| Admin 登录后 POS 身份 | POS 后续结算 `create_by` 仍为 A；后台库存/会员等操作归 B。 |
| Admin 退出、401、关闭 | 均仅影响 Admin；关闭重开必须再次登录。 |
| 最小化、恢复、重复入口 | 会话保持；始终仅一个 Admin BrowserWindow。 |
| POS 核心 | 结算、退款、挂单、补货、会员绑定和客显不回归。 |

## 实施顺序

1. 实现并测试 Admin 窗口、preload 与单实例生命周期，不改变 POS 核心流程。
2. 实现 `entry=admin` 的安全登录后导航，证明两个 renderer 的 token/Pinia 独立。
3. 实现候选 DTO、loopback/default-tenant 守卫和认证集成测试，再接入 Admin 下拉。
4. 替换 POS 后台入口，补退出、401、关闭/重开和重复点击回归。
5. 打包 Windows EXE，按矩阵实测 preload 打包、会话销毁及 POS 收银不受影响。

## 实施评审结论

**具备实施条件，建议按 UX-1.1 单独切片开工。** 实现不得扩大为默认密码、JWT 撤销或全局权限重构。

## 实施结果（2026-09-23）

已完成本文列出的 Electron 窗口隔离、最小 preload IPC、后台入口登录导航、最小候选接口及精确安全白名单。候选接口使用配置化的租户请求头名，拒绝该头的任何覆盖请求和非 loopback 请求，并设置 `Cache-Control: no-store`。新增的集成测试覆盖启用账号过滤、最小返回、无缓存头、租户覆盖拒绝与非本机来源拒绝；测试事务均回滚。

已验证：

- `mvn -q -pl qk-money-app/money-app-system -am test-compile` 通过。
- 在隔离 `money_pos_test` 库上运行 `SysAuthControllerIntegrationTest` 通过（2 tests, 0 failures/errors）。
- `git diff --check` 通过。

本工作环境没有可用的 Linux `npm`，且 Windows Node 在 WSL 中无法启动，因此前端 production build 与 Windows EXE 实机回归仍须在开发机完成。重点验收本文“回归矩阵”中的 POS/Admin 身份隔离、关闭重开重新登录、重复入口单窗口及预加载文件随打包产物加载。

实施时采用以下精确分工：

1. `Login.vue` 读取固定 `entry=admin` 并决定登录后 `/dashboard` 或 `/pos`；`user.js` 继续只负责 token 登录，不感知窗口身份。
2. `SysAuthController` 验证请求是 loopback、没有覆盖租户 header，再委派认证服务；认证服务委派 `SysUserService` 查询候选，避免认证层直接持久化访问用户实体。
3. 候选查询只选择 username/nickname/enabled 所需字段，返回独立 VO；绝不将 `SysUserVO`、角色或密码数据带到匿名路径。
4. Admin 的 close 处理必须采用可等待的“清理 storage 后销毁”流程，防止 `closed` 事件发生后再访问已销毁的 webContents。

## 实施前确认

按产品规则，所有有效系统账号均可认证后台、关闭后台后每次重新登录、下拉只展示用户名和昵称，且候选接口只服务本机回环访问。若需要跨机后台、按租户选择账号或隐藏没有后台菜单的账号，应先单独设计网络/租户/权限边界。
