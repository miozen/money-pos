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

最新架构提交是 **`37eaba1 docs(ad-3.4): design gms pos goods snapshot`**；它只包含 AD-3.4
设计文档和清单/台账更新。此前的相邻提交为：

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
- AD-3 正在实施：AD-3.1、AD-3.2、AD-3.3、AD-3.4 已关闭；AD-3.4.1 是当前唯一下一最小任务。
- AD-2（共享 Entity 物理归属）、AD-4（物理 Maven 拆分）、AD-5（CI 门禁）、AD-6（Java 17
  基线）均未完成，不能因 P2 已关闭而误报“架构调整全部完成”。

架构扫描当前应保持：Controller → Mapper、跨 Feature Mapper/ServiceImpl、跨 Feature 实现 import
均为 0；共享 `com.money.entity` import 约 74 个 Feature 文件，仍是报告型债务，尚不可设为失败门禁。

## 当前任务：AD-3.4.1

**实施 GMS→POS 商品搜索快照，收敛 `GoodsPosFacade` 的通用商品实体查询。**

先完整阅读 `MoneyPOS-AD-3.4-Gms-Pos-Goods-Search-Snapshot-Design.md`。已固定的实施边界：

- 对外路由保持 `GET /gms/goods/pos-search?keyword=...`，响应继续为 `List<GmsGoodsVO>`；不改页面字段、
  数据库表、Flyway 或事务边界。
- 在 `money-app-api` 新增独立、Java 8 普通不可变 DTO + 查询端口；GMS 在自身内部以 mapper /
  价格服务实现。不得复用或污染现有 `PosGoodsCatalogQuery`：后者服务 TRADE `PosService`，字段和
  搜索口径不同。
- `GoodsPosFacade` 改为只依赖新端口并把快照映射回旧 `GmsGoodsVO`，删除它对
  `GmsGoodsService.lambdaQuery()` 和 `GmsGoods` 的依赖。
- **兼容关键点**：旧 MyBatis-Plus 链未分组，实际 SQL 语义是
  `barcode LIKE keyword OR name LIKE keyword OR (mnemonic_code LIKE keyword AND status = 'SALE')`。
  条码/名称命中非 `SALE` 商品仍可能返回；本次边界迁移必须用回归锁定此行为，不能擅自修成全部
  商品必须 `SALE`。助记码使用传入原样关键字，不能沿用既有契约的 `toUpperCase()`；空字符串保持
  旧 `LIKE '%%'` 语义。
- GMS 输出主档和等级价格/券原值；Facade 保留 SYS 品牌券策略：品牌策略关闭时将等级券额强制置零。
  `SysBrandConfigMapper` / `SysBrandConfig` 的边界不属于 AD-3.4.1，后续单列任务。
- 需要覆盖：条码/名称命中非 `SALE`、助记码命中非 `SALE`、原样小写助记码、空关键字、价格矩阵，
  以及策略关闭时券额为零。

建议的验收搜索：`GoodsPosFacade` 不再 import `GmsGoodsService` 或 `GmsGoods`，也不调用
`lambdaQuery()`。

## 后续编号顺序

AD-3.4.1 完成后，按债务清单执行唯一下一最小任务：**AD-2.1——共享 Entity 消费者再盘点与首切片选择**。
不能直接移动一批 Entity；先逐项标记本域持久化、跨域泄露和兼容桥，并选一个低风险单一所有者切片。

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
