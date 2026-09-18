# MoneyPOS 双电脑接力协议

本协议与 `MoneyPOS-AI-Handoff.md`、`WSL-双电脑开发测试环境指南.md` 一起使用，目标是让两台开发电脑在
同一 `dev` 分支上安全、可重复地接力，而不共享本机数据库或临时运行状态。

## 交接前：当前电脑

1. 确认在 `dev`：`git branch --show-current`。
2. 先检查并处理所有本地变更：`git status --short`。本轮已明确授权的前端、开发配置、文档和架构改动均必须
   以可审阅提交进入 Git；不要把 IDE、`target/`、`node_modules/`、日志、数据库文件或本机密钥加入提交。
3. 后端改动至少执行与任务匹配的专项/全量测试；所有提交执行 `mvn -q package -DskipTests`、
   `scripts/architecture-scan.sh --check-new` 与 `git diff --check`。
4. 更新 `MoneyPOS-AI-Handoff.md`：当前任务、扫描计数、数据库测试命令、已完成提交、唯一下一最小任务；架构任务
   同时更新债务清单和实施台账。
5. 推送前读取远端：`git fetch origin`；若远端已有新提交，先以 `git pull --ff-only origin dev` 快进同步，
   不以强推覆盖另一台电脑的工作。
6. 推送：`git push origin dev`。将推送后的提交号、当前任务编号和验证结果交给下一位接手者。

## 接手前：另一台电脑

```bash
cd ~/projects/money-pos
git switch dev
git fetch origin
git pull --ff-only origin dev
git status --short
git log --oneline -8
```

工作区应干净后再开始任务。若有自己的未提交改动，先自行提交为独立变更，或显式暂存；不要在未确认范围时使用
`git reset --hard`、广泛 `checkout` 或 `restore`。出现非快进或冲突时停止推送，保留两侧提交并人工决定合并顺序。

## 数据库与验证隔离

- 每台电脑维护自己的 `money_pos` 开发库和 `money_pos_test` 测试库；代码同步不等于数据同步。
- 本机开发连接、首次安装、启动和需要同步测试数据的操作见 `WSL-双电脑开发测试环境指南.md`。
- 自动测试只能使用 `money_pos_test`，并从 `application-dev.yml` 读取可覆写的 `MONEY_DB_*` 凭据；不得把测试
  指向开发库或生产库。
- 结构变化必须以 Flyway 脚本提交；下一台电脑构建/启动后让 Flyway 升级自己的本地库。业务数据需要同步时，按
  WSL 指南显式导出/导入，并确认没有真实客户或生产数据。

## 当前接力资料

- 分支与远端：`dev` → `origin/dev`。
- 最新架构提交：以 `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md` 和 `git log --oneline` 为准。
- 当前最小任务：以 `MoneyPOS-AI-Handoff.md` 与 `MoneyPOS-Architecture-Debt-Backlog.md` 的“当前任务”为准。
- 共享 Entity 迁移前必须阅读对应选择文档；当前 AD-2.11 的边界在
  `MoneyPOS-AD-2.10-Fifth-Entity-Slice-Selection.md`。

## 冲突最小化约定

- 同一时刻只让一台电脑改动同一个文件或同一个最小架构任务。
- 开始前先拉取，结束后尽快提交和推送；提交保持单一目的，前端、环境文档和架构切片需要明确说明范围。
- `application-dev.yml` 可提交环境变量默认值，但禁止写入真实生产密码、访问令牌、私钥或数据库备份。
- 不能安全自动合并时，保留冲突证据并交由操作者选择，不猜测另一台电脑的意图。
