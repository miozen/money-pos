# MoneyPOS 项目接力说明

## 目的

本文件供新的 AI 对话或协作者接手 MoneyPOS 的后端架构调整。开始改动前，必须完整阅读：

1. 本文件；
2. `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md`；
3. `MoneyPOS-Architecture-Debt-Backlog.md`；
4. 当前最小任务的设计文档。

台账记录已完成工作的事实，债务清单决定后续编号与当前顺序；两者冲突时，以台账的实施事实和
债务清单的当前待办共同校正，不能回退到历史 P2 清单继续编号。

## 仓库与操作红线

- 工作目录：`/home/mio/projects/money-pos`。
- Maven 根目录：`/home/mio/projects/money-pos/money-pos`。
- 当前分支：`dev`；仅在 `dev` 开发。
- **可本地提交，但用户明确要求不得推送**，尤其不得推送 `main`。
- 不使用 `git reset --hard`，不使用广泛的 `git checkout` / `git restore`。
- 每次只 `git add` 当前最小任务明确修改的文件；提交前先检查暂存区。

## 必须保留的现有用户改动

当前工作区并不干净。下列内容属于用户或其他工作，禁止重置、覆盖、暂存或混入架构提交：

- `money-pos-web/**` 的所有现有修改；
- `money-pos/qk-money-app/money-app-biz/src/main/java/com/money/QkMoneyApplication.java`；
- `money-pos/qk-money-app/money-app-biz/src/main/resources/application-dev.yml`；
- 未跟踪的 `docs/WSL-双电脑开发测试环境指南.md`（只读参考）。

开始工作先执行 `git status --short`，提交后再次确认只留下这些用户改动。

## 当前基线与已完成架构工作

接力前的最新文档基线是 **`f7dc5c4 docs: refresh architecture handoff`**；它记录 AD-3.4.1 的
实施起点。此前的相邻架构提交为：

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
均为 0；共享 `com.money.entity` import 约 74 个 Feature 文件，仍是报告型债务，尚不可设为失败门禁。

## 当前任务：AD-2.1

**共享 Entity 消费者再盘点与首切片选择。**

先重新执行共享 Entity import 扫描，并以当前代码逐项建立归属表。已固定的实施边界：

- 先完成设计/盘点，不移动任何 Entity、Mapper 或 DTO；AD-2.2 必须以单独提交实施。
- 对每个 Feature 的 `com.money.entity` import 标记“本域持久化合法 / 跨域泄露 / 兼容桥”，并记录
  所有者、调用目的、Mapper XML、序列化和事务影响。
- 从已分类对象选择一个低风险、单一所有者切片；不得以 import 数量或包名整洁为由批量移动。
- 不改 HTTP 路由、页面字段、表/Flyway、既有事务边界或对外 DTO。

## 后续编号顺序

AD-2.1 完成后，按债务清单执行唯一下一最小任务：**AD-2.2——首个 Entity 物理归属迁移**。不能直接
移动一批 Entity；只迁移 AD-2.1 已明确所有者和消费者面都可控的单一切片。

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

1. **本次完成什么**：编号、实际改动、本地提交号；
2. **验证结果**：专项、全量、打包、架构门禁、`git diff --check` 分别说明；
3. **遗留风险或延期项**；
4. **下一轮做什么**：只给一个带编号的最小任务。

代码、测试、文档和本地提交未形成闭环时，明确写“未完成”，不得称“已完成”。

## 关键参考

- `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md`
- `MoneyPOS-Architecture-Debt-Backlog.md`
- `MoneyPOS-AD-3.2-IService-Entity-Call-Audit.md`
- `MoneyPOS-AD-3.3-Sys-Dictionary-Read-Contract-Migration.md`
- `MoneyPOS-AD-3.4-Gms-Pos-Goods-Search-Snapshot-Design.md`
- `MoneyPOS-P2-Final-Acceptance-Review.md`
- `WSL-双电脑开发测试环境指南.md`（用户未跟踪，仅只读）
