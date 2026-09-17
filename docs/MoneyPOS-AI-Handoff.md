# MoneyPOS 项目接力说明

## 目的

本文件供新的 AI 对话或协作者接手 MoneyPOS 架构调整工作。开始任何改动前，请完整阅读本文件、`MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md` 和当前阶段清单。

## 当前仓库状态

- 工作目录：`/home/mio/projects/money-pos`
- Maven 根目录：`/home/mio/projects/money-pos/money-pos`
- 工作分支：`dev`。
- 仅在 `dev` 开发；**用户明确要求提交前不得推送**，尤其不得推送 `main`。
- 当前已提交的最新架构调整提交：`1366b9e refactor(p2): isolate finance member assets`。
- P2.3.3 的所有半成品已撤回，代码和 P2 文本恢复到 `1366b9e` 状态；不要把 P2.3.3 标记为已完成。

## 必须保留的用户改动

工作区不是干净状态。下列改动属于用户/其他工作，不得重置、覆盖、提交或混入架构调整提交：

- `money-pos-web/**` 的现有修改；
- `money-pos/qk-money-app/money-app-biz/src/main/java/com/money/QkMoneyApplication.java`；
- `money-pos/qk-money-app/money-app-biz/src/main/resources/application-dev.yml`；
- 未跟踪的 `docs/WSL-双电脑开发测试环境指南.md`。

每次提交只 `git add` 本轮明确修改的文件。不得使用 `git reset --hard` 或广泛的 `git checkout`/`git restore`。

## 已完成工作

阶段 0、1、2、3、4、5 与 P1 的已完成记录以实施台账为准。P2 当前已完成：

- P2.0：FIN/HOME 报表读模型盘点；
- P2.1：HOME 库存估值查询契约；
- P2.2.1～P2.2.5：HOME 订单、趋势、品牌、会员图表快照及验收；
- P2.3：FIN 财务大盘快照设计；
- P2.3.1：GMS 财务库存单据快照，提交 `048077f`；
- P2.3.2：UMS 会员资产快照，提交 `1366b9e`。

P2.3.2 后，FIN 已不再直接访问 UMS 的 Entity、Mapper 或 `UmsMemberService`；架构扫描中的 FIN→UMS 实现 import 已消除。

## 当前清单与下一任务

权威清单：`docs/MoneyPOS-P2-Report-Read-Model-Checklist.md`。

当前下一项是：

**P2.3.3：迁移 FIN 财务大盘的 TRADE 订单/支付读取。**

设计依据：`docs/MoneyPOS-P2.3-Finance-Dashboard-Read-Snapshot-Design.md`。应按数据所有者在 TRADE 内实现 Java 8 兼容的普通不可变 DTO/查询契约，FIN 只依赖 `money-app-api` 契约。不要使用 Java `record`。

建议拆分顺序：

1. 当日订单核心指标与今日资产概览；
2. 支付渠道日汇总、七日退款趋势、渠道优惠；
3. P2.3.4：删除 FIN 剩余跨域订单/支付 Mapper 依赖，补回归并验收。

兼容约束：

- 订单金融有效状态：`PAID`、`PARTIAL_REFUNDED`、`REFUNDED`；
- 当前财务日查询使用当天 `00:00:00` 至 `23:59:59.999999999` 闭区间；
- 支付净额：全额退款订单为 0，否则使用 `net_amount`，缺失时回退 `pay_amount`；
- 退款趋势：`SUM(pay_amount) - SUM(final_sales_amount)`，负数钳制为 0；
- 渠道优惠：按天聚合 `actual_coupon_deduct` 和 `use_voucher_amount`；
- 不改 HTTP 路由、页面字段、数据库表、Flyway 或事务边界。

## Java 版本说明

- 本机运行 JDK 是 Java 17；
- 主 Maven 根 POM仍声明 `java.version`、`maven.compiler.source`、`maven.compiler.target` 为 `1.8`；
- 因此 `money-app-api` 及所有主模块必须使用 Java 8 源码兼容语法。可使用普通类、`final` 字段、构造函数和 getter；不要使用 `record`、文本块等新语法。
- Java 17 基线升级可以作为未来独立任务评估，不能与 P2.3.3 混做。

## 验证命令

在 Maven 根目录执行。测试使用隔离的 MariaDB `money_pos_test`；不要指向开发库：

```bash
test_db_user=$(perl -ne 'print "$1\n" if /username: \$\{MONEY_DB_USERNAME:([^}]*)\}/' qk-money-app/money-app-biz/src/main/resources/application-dev.yml)
test_db_password=$(perl -ne 'print "$1\n" if /password: \$\{MONEY_DB_PASSWORD:([^}]*)\}/' qk-money-app/money-app-biz/src/main/resources/application-dev.yml)
MONEY_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/money_pos_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' MONEY_TEST_DB_USERNAME="$test_db_user" MONEY_TEST_DB_PASSWORD="$test_db_password" MONEY_TEST_DB_NAME='money_pos_test' mvn -q test
```

专项 FIN 回归：

```bash
MONEY_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/money_pos_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' MONEY_TEST_DB_USERNAME="$test_db_user" MONEY_TEST_DB_PASSWORD="$test_db_password" MONEY_TEST_DB_NAME='money_pos_test' mvn -q -pl qk-money-app/money-app-biz -am test -Dtest=FinanceFeatureIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

打包与架构门禁：

```bash
mvn -q package -DskipTests
cd ..
scripts/architecture-scan.sh --check-new
git diff --check
```

MariaDB 未启动或沙箱限制时，需要用户授权；不要猜测密码，也不要改开发数据库。

## 协作与汇报规则

每轮代码修改完成后，必须在回复中清晰写出：

1. **本次完成什么**：对应的清单编号、实际改动和提交号；
2. **验证结果**：专项/全量测试、打包、门禁分别是否通过；
3. **遗留风险或延期项**；
4. **下一轮做什么**：仅一个与清单对应的最小任务。

没有完成代码、测试、文档和本地提交闭环时，必须明确写“未完成”，不得使用“已完成”措辞，也不要反复要求用户确认同一个下一步。

## 参考文档

- `docs/MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md`
- `docs/MoneyPOS-P2-Report-Read-Model-Checklist.md`
- `docs/MoneyPOS-P2.0-Report-Read-Model-Inventory.md`
- `docs/MoneyPOS-P2.3-Finance-Dashboard-Read-Snapshot-Design.md`
- `docs/WSL-双电脑开发测试环境指南.md`（用户未跟踪文档，只读参考）
