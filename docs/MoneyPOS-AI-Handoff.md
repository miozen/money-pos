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

每轮的完成定义是：**编号任务 → 任务范围内的代码/测试/文档 → 验证 → 本地提交 → 推送 `origin/dev` → 确认 `ahead=0` 且 `behind=0`**。任一步未完成，只能报告“本地进行中”或“本地完成待同步”，不能作为另一台电脑或新 AI 对话继续下一编号任务的基线。详细命令、数据库隔离和冲突处理见 `MoneyPOS-双电脑接力协议.md`。


## 仓库与操作红线

- WSL 工作目录：`/home/mio/projects/money-pos`。
- Maven 根目录：`/home/mio/projects/money-pos/money-pos`。
- 当前分支：`dev`；仅在 `dev` 开发。
- 远端为 SSH `git@github.com:miozen/money-pos.git`。开始前 `git fetch origin`、`git pull --ff-only origin dev`；
  完成后可提交并推送到 `origin/dev` 以供双电脑接力。推送后再次 `git fetch origin`，确认无 ahead/behind；不得推送
  `main`，不得强推或覆盖另一台电脑的提交。
- 不使用 `git reset --hard`，不使用广泛的 `git checkout` / `git restore`。
- 每次只 `git add` 当前最小任务明确修改的文件；提交前先检查暂存区。
执行环境为 Windows PowerShell 宿主 + WSL Ubuntu 仓库。所有含 Bash 变量、命令替换、正则、凭据或多行逻辑的命令，必须通过 `MoneyPOS-双电脑接力协议.md` 的 `$wslScript` 单引号 here-string 模板，以标准输入传入 `wsl.exe ... bash -s`；外层 PowerShell 只能传递脚本，不解释业务语法。看到 PowerShell `ParserError` 时，该命令尚未进入 WSL，先修正封装，不要将其记作 Maven、数据库或代码失败。


## 工作区所有权与同步

双电脑接力的起点应是干净且已同步的 `dev`：开始前执行 `git fetch origin`、`git pull --ff-only origin dev` 和 `git status --short`。若发现未提交改动，先确认其所有者和任务归属；只暂存当前编号任务明确修改的文件，绝不把未知改动混入架构提交。不得用 `git reset --hard`、广泛 `checkout` 或 `restore` 清理他人工作。

每轮结束必须先提交、推送，再执行 `git fetch origin` 和 `git status -sb` 确认与 `origin/dev` 对齐。若发生非快进、冲突或推送失败，停止开始下一编号任务，保留双方提交并按双电脑协议处理。


## 当前基线与已完成架构工作

接力前已同步基线是 **`eb7bc90 feat(ad-2.55): migrate order detail entity`**；AD-2.56 已在本地完成并待提交/推送。此前的相邻架构提交见 `git log --oneline`。

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
- AD-2（共享 Entity 物理归属）、AD-4（物理 Maven 拆分）、AD-5（CI 门禁）、AD-6（Java 17
  基线）均未完成，不能因 P2 已关闭而误报“架构调整全部完成”。

架构扫描当前应保持：Controller → Mapper、跨 Feature Mapper/ServiceImpl、跨 Feature 实现 import
均为 0；共享 `com.money.entity` import 当前为 25 个 Feature 文件，所有权登记为 2、跨域桥为 0、通配符路径为 2；文件总数仍为报告型债务。`--check-new`
现已阻止新增非所有者/未登记 Entity 与新通配符，但不按该总数失败。

## 当前任务：AD-2.57

**UMS `UmsMember` Entity 物理归属迁移。**

先完整阅读实施台账、债务清单和 `MoneyPOS-AD-2.56-Twenty-Eighth-Entity-Slice-Selection.md`。将
`UmsMember` 迁入 UMS 持久化边界，更新 Mapper、档案、导入、资产、日志、充值、查询、Controller 和测试。

- 保持会员路由/DTO/VO、逻辑删除、导入、资产 Excel、充值/红冲、余额原子扣减、结账/退款、券/日志、POS/排行与 FIN/HOME 快照；不得改变表/Flyway、事务边界或业务公式。
- 新增 UMS 本地 Mapper CRUD 回归，并保留会员管理、导入、资产、充值、结账/退款、POS、排行和 FIN/HOME 回归。
- 以 AD-2.55 后扫描实测为基线：共享 Feature import 25、所有权登记 2、跨域桥 0、通配符路径 2；完成后预期登记 1，桥和通配符不得扩大。

## 后续编号顺序

AD-2.57 是唯一允许开始的迁移切片；门禁仅阻止新引入的、已分类非所有者 Entity 契约，不能把当前报告数量直接设为失败规则。

## Java 与设计约束

- 本机 JDK 为 17，但主 POM `java.version` / compiler source / target 仍为 **1.8**。
- `money-app-api` 与主模块必须使用 Java 8 源码：普通类、`final` 字段、构造函数和 getter；禁止
  `record`、文本块及其他较新语法。
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
