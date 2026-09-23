# MoneyPOS 项目接力说明

## 目的

本文件供新的 AI 对话或协作者接手 MoneyPOS 的后端架构调整。开始改动前，必须完整阅读：

1. 本文件；
2. `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md`；
3. `MoneyPOS-Architecture-Debt-Backlog.md`；
4. 当前最小任务的设计文档。

台账记录已完成工作的事实，债务清单决定后续编号与当前顺序；两者冲突时，以台账的实施事实和
债务清单的当前待办共同校正，不能回退到历史 P2 清单继续编号。

## 编号任务与双机接力总规则

`MoneyPOS-Architecture-Debt-Backlog.md` 的“当前任务”是唯一允许开始的任务编号。每轮只完成一个编号任务：先读其设计边界，再实施、验证、更新台账/债务清单/本文件，最后提交。不得在迁移任务中自行选择下一切片，也不得在选择任务中夹带迁移实现。

每轮的完成定义是：**编号任务 → 任务范围内的代码/测试/文档 → 验证 → 本地提交 → 推送 `origin/dev` → 确认 `ahead=0` 且 `behind=0`**。任一步未完成，只能报告“本地进行中”或“本地完成待同步”，不能作为另一台电脑或新 AI 对话继续下一编号任务的基线。若 Codex 工具宿主因网络/DNS 无法推送，用户应在其交互 WSL 终端按 `MoneyPOS-双电脑接力协议.md` 的 SSH-over-443 固定命令完成推送并回传结果；AI 不得重复创建提交。


## 仓库与操作红线

- WSL 工作目录：`/home/mio/projects/money-pos`。
- Maven 根目录：`/home/mio/projects/money-pos/money-pos`。
- 当前分支：`dev`；仅在 `dev` 开发。
- 远端为 SSH `git@github.com:miozen/money-pos.git`。开始前 `git fetch origin`、`git pull --ff-only origin dev`；
  完成后可提交并推送到 `origin/dev` 以供双电脑接力。受限工具宿主推送失败时，由用户在交互 WSL 执行
  `git push ssh://git@ssh.github.com:443/miozen/money-pos.git dev:dev`，再 `git fetch origin` 与 `git status -sb`
  复核；不得推送 `main`，不得强推或覆盖另一台电脑的提交。
- 不使用 `git reset --hard`，不使用广泛的 `git checkout` / `git restore`。
- 每次只 `git add` 当前最小任务明确修改的文件；提交前先检查暂存区。
执行环境为 Windows PowerShell 宿主 + WSL Ubuntu 仓库。所有含 Bash 变量、命令替换、正则、凭据或多行逻辑的命令，必须通过 `MoneyPOS-双电脑接力协议.md` 的 `$wslScript` 单引号 here-string 模板，以标准输入传入 `wsl.exe ... bash -s`；外层 PowerShell 只能传递脚本，不解释业务语法。看到 PowerShell `ParserError` 时，该命令尚未进入 WSL，先修正封装，不要将其记作 Maven、数据库或代码失败。


## 工作区所有权与同步

双电脑接力的起点应是干净且已同步的 `dev`：开始前执行 `git fetch origin`、`git pull --ff-only origin dev` 和 `git status --short`。若发现未提交改动，先确认其所有者和任务归属；只暂存当前编号任务明确修改的文件，绝不把未知改动混入架构提交。不得用 `git reset --hard`、广泛 `checkout` 或 `restore` 清理他人工作。

每轮结束必须先提交、推送，再执行 `git fetch origin` 和 `git status -sb` 确认与 `origin/dev` 对齐。若发生非快进、冲突或推送失败，停止开始下一编号任务，保留双方提交并按双电脑协议处理。


## 当前基线与已完成架构工作

接力前必须使用最新已推送的 `dev`；AD-2.59 将最后一个共享 Entity `OmsOrder` 迁入 TRADE 后，AD-2
共享 Entity 物理归属已关闭。此前的相邻架构提交见 `git log --oneline`。

| 提交 | 已闭环事项 |
| --- | --- |
| `80b72d5` | AD-3.3：业务 Feature 使用 SYS 字典值/描述查询契约，不再读取 `SysDictDetail` Entity / Mapper。 |
| `490290b` | AD-3.2：跨域 `IService<Entity>` 实际调用审计。 |
| `35a1495` | AD-3.1：会员画像排行榜 DTO 不再暴露实现类嵌套类型。 |
| `361a7a9` | AD-1.3b：HOME 日快照启动刷新、每 5 分钟刷新、并发闸门，GET 已纯读。 |
| `dd730c0` | AD-1.3a：HOME 快照命令/查询分离与原子写入。 |
| `0a734bb` | P2.6：TRADE `OmsOrderController` 不再兼容调用 FIN 实现。 |
| `8a60763` | P2 最终收口：FIN/HOME 报表读模型跨所有者读取已契约化。 |

宏观状态：

- P1 已关闭：GMS/UMS/TRADE 既定跨域场景以 Entity-free 读快照/端口收敛。
- P2 已关闭：FIN/HOME 报表不再直接读取其他 Feature 的 Entity、Mapper 或实现服务；P2.6 亦关闭。
- AD-1 已关闭：HOME `DecisionEngine` 的写入边界已治理；采用启动即刷新 + 每 5 分钟刷新，
  单 JVM `AtomicBoolean` 防重入，失败保留上一份有效快照并记录日志。多实例部署尚无分布式锁，
  这是已记录的运行风险。
- AD-3 已关闭：AD-3.1 至 AD-3.4.1 均已闭环；遗留实现类型与跨域通用实体查询风险已按当前盘点收敛。
- AD-2（共享 Entity 物理归属）、AD-4（物理 Maven 拆分）、AD-5（CI 门禁）、AD-6（Java 17 基线评估）
  和 AD-7（运行时启动负担与无效能力盘点）均已关闭；AD-6.1 已完成主 reactor Java 17 基线实施、回归、打包、门禁和 Windows EXE 既有数据安装验收。

架构扫描当前应保持：Controller → Mapper、跨 Feature Mapper/ServiceImpl、跨 Feature 实现 import
均为 0；共享 `com.money.entity` import、所有权登记、跨域桥和通配符路径均为 0。`--check-new`
持续阻止新增未登记 Entity 与新通配符，且不把其他历史报告型指标误接为失败规则。

## 当前状态：无进行中的获准架构改造任务；ME-1 产品功能正在本地实施

**启动优化已冻结；收银小票打印已验收；近期 POS UX 源码改造已完成。**

用户已授权并冻结“会员仓库 / 品牌会员权益”产品方向。该功能不使用 AD 编号，实施清单为
`MoneyPOS-ME-1-Member-Entitlement-Implementation-Checklist.md`；当前已完成 ME-1.1（UMS 品牌档位和
权益账本基础），下一最小切片为 ME-1.2（仅 QUANTITY 延迟履约）。ME-1.1 的隔离 `money_pos_test` 专项 3 项、
既有 Checkout 17 项、Java 编译/打包、架构门禁和空白检查均通过；未提交、未推送时不得据此在另一台电脑接力。

用户于 2026-09-22 确认 Windows 打包版重复启动约 15 秒可接受，明确决定不继续启动测量或优化。因此不得启动
ENV-1 剩余测量、HOME 延后刷新、Electron 健康检查调整或“无效代码裁剪”。收银小票打印已实机通过；钱箱仅在门店实际启用时再单独确认。当前自动开箱语义是：结账订单存在正额现金支付、且打印配置允许时开箱；并非“仅有找零时开箱”，补打不自动开箱。

- AD-5.1 已关闭：`Architecture Gate / additions-only` 的正常 dev 运行通过；临时 PR 中的 Controller→Mapper
  违规按预期红灯，分支已删除。现有 EXE 发布工作流保持不变。
- AD-6.1 已关闭：新 Windows EXE 安装包在 `D:\30\_Data\WANXIANG-POS\vana-pos` 使用 `D:\WanXiang\POS-Data` 的既有数据启动，health 为 UP，收银窗口可用。
- ENV-1 的首次初始化、实例复用、端口冲突和关闭流程已由用户冻结，不是待办或裁剪前置条件。
- ENV-2 收银小票打印已通过；钱箱若启用仍需一次独立实机确认。
- 9 月 23 日的 POS UX 提交已经在 `dev`：后台独立会话与密码登录、POS 候选账号登录、主窗口退出确认、库存异常行补货、空购物车安全切换收银员，以及现金结账自动开箱。开始新 UX 工作前先以当前源码和 `git log` 校正 9 月 22 日产品台账的历史表述。

## 后续编号顺序

AD-5、AD-5.1、AD-6 和 AD-6.1 均已闭环。AD-7 的后续实施与 ENV-1 测量均已冻结；SQLite 当前不在计划内。任何新工作先以 `MoneyPOS-Current-Architecture-and-Business-Scenarios.md` 为业务边界参考，完成盘点、编号并取得用户授权。

## Java 与设计约束

- Windows 随包 JRE、CI 与主 reactor POM 均为 Java 17；Compiler Plugin `release=17` 与 Enforcer `[17,)`
  已固定最低基线。生产源码仍默认使用保守 Java 写法；不得因为基线升级顺带引入 `record` 或文本块。
- Spring Boot 2.7 仍使用 `javax`；Java 17 基线不授权 `javax`/`jakarta` 迁移或 Boot 3 升级。
- 不以“包名整洁”为理由批量改造；按数据所有者设计窄 DTO/查询契约，读模型和命令边界分开。
- 未经独立任务授权，不改变 HTTP 路由、响应字段、表/Flyway、交易事务边界或既有业务公式。

## 验证命令

从 Maven 根目录执行。测试只能使用隔离 MariaDB `money_pos_test`，不得指向开发库，也不得猜测密码：

```bash
test_db_user=$(perl -ne 'print "$1\n" if /username: \$\{MONEY_DB_USERNAME:([^}]*)\}/' qk-money-app/money-app-biz/src/main/resources/application-dev.yml)
test_db_password=$(perl -ne 'print "$1\n" if /password: \$\{MONEY_DB_PASSWORD:([^}]*)\}/' qk-money-app/money-app-biz/src/main/resources/application-dev.yml)
MONEY_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/money_pos_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' MONEY_TEST_DB_USERNAME="$test_db_user" MONEY_TEST_DB_PASSWORD="$test_db_password" MONEY_TEST_DB_NAME='money_pos_test' mvn -q test
```

### 测试配置与权限防错规则

- 上述命令是唯一认可的隔离数据库回归入口：凭据只能从 `application-dev.yml` 中既有的
  `MONEY_DB_*` 默认值提取；项目并不存在 `.env.test`，不得假设、创建或读取它。
- 运行前只可做非敏感预检：提取的用户名必须非空，且 `MONEY_TEST_DB_URL` 必须指向
  `127.0.0.1:3306/money_pos_test`。不得输出密码、改用 MySQL 系统维护账号，或把测试指向开发库。
- 若普通沙箱报 `Operation not permitted` / 本机 3306 连接受限，应使用**同一条命令、同一凭据来源、同一
  `money_pos_test` URL**申请受控本机执行；这属于环境权限限制，不得以替换配置来源规避。

AD-3.4.1 优先增加并执行相关 GMS/POS 集成回归，再执行上述全量测试。随后：

```bash
mvn -q package -DskipTests
cd ..
scripts/architecture-scan.sh --check-new
git diff --check
```

MariaDB 未启动、凭据不可用或沙箱限制时，需要用户授权；不要改用开发库。仅文档任务可说明专项/全量
测试不适用，但仍要跑打包、门禁和空白检查。

## 汇报与提交闭环

每轮完成后必须清晰报告：

1. **本次完成什么**：编号、实际改动、本地提交号、`origin/dev` 推送与对齐状态；
2. **验证结果**：专项、全量、打包、架构门禁、`git diff --check` 分别说明；
3. **遗留风险或延期项**；
4. **下一轮做什么**：只给债务清单中的一个带编号最小任务。

代码、测试、文档和本地提交未形成闭环时，明确写“未完成”，不得称“已完成”。

## 关键参考

- `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md`
- `MoneyPOS-Architecture-Debt-Backlog.md`
- `MoneyPOS-AD-3.2-IService-Entity-Call-Audit.md`
- `MoneyPOS-AD-3.3-Sys-Dictionary-Read-Contract-Migration.md`
- `MoneyPOS-AD-3.4-Gms-Pos-Goods-Search-Snapshot-Design.md`
- `MoneyPOS-P2-Final-Acceptance-Review.md`
- `WSL-双电脑开发测试环境指南.md`（已纳入双电脑接力资料）
- `MoneyPOS-双电脑接力协议.md`（提交/推送、交接、冲突和数据库隔离流程）
