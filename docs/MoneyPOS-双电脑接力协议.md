# MoneyPOS 双电脑接力协议

本协议与 `MoneyPOS-AI-Handoff.md`、`WSL-双电脑开发测试环境指南.md` 一起使用，目标是让两台开发电脑在
同一 `dev` 分支上安全、可重复地接力，而不共享本机数据库或临时运行状态。
## Codex Windows 宿主与 WSL 执行规则

本 Codex 桌面任务的工具宿主是 Windows PowerShell；仓库、Git、Maven、测试数据库和开发命令则位于 WSL Ubuntu。因此每条仓库命令都经历“PowerShell 启动 `wsl.exe` → WSL Bash 执行”的两层解析。Windows 宿主不是错误，但将 Bash 的 `$变量`、`$(命令替换)`、正则、反斜杠或引号直接嵌入外层 PowerShell 命令会在进入 WSL 前被错误解析。

涉及变量、正则、数据库凭据、Maven 参数或多行逻辑时，必须使用以下唯一模板：外层 PowerShell 只保存并传递单引号 here-string，所有业务语法只由 WSL Bash 解释。

```powershell
$wslScript = @'
set -euo pipefail
cd /home/mio/projects/money-pos/money-pos
# 以下内容只使用 Bash 语法，例如 $变量、$(命令替换)、Perl 正则和 Maven 命令
'@
$wslScript | wsl.exe -d Ubuntu -- bash -s
```

简单的无变量只读命令可以直接使用 `wsl.exe -d Ubuntu -- bash -lc 'cd ... && git status -sb'`。只要命令含有上述 Bash 语法，就必须通过 here-string 的标准输入执行 `bash -s`，不得将其作为 `bash -lc` 的命令行参数。若报错来自 `ParserError`、PowerShell 或 Windows 路径，先修正外层封装；不得误报为 WSL、Maven 或数据库失败，也不得重复消耗测试执行。


## 强制的编号任务闭环

`MoneyPOS-Architecture-Debt-Backlog.md` 是架构调整任务编号、状态和顺序的唯一来源。每轮只能从其“当前任务”
开始一个编号任务（例如 AD-2.11）；不得依据历史对话、猜测或相邻提交自创编号、跳号或并行开始下一个切片。

一次任务必须按以下闭环结束：

1. **开始**：读取当前编号任务及其设计/选择文档，确认边界与验收项。
2. **实施**：只完成该编号授权的范围；选择任务不实施迁移，迁移任务不提前选择下一切片。
3. **验证**：执行与任务相称的测试、架构门禁和空白检查；纯文档任务不构建，但仍执行适用的门禁与 `git diff --check`。
4. **记录**：更新实施台账、债务清单、AI 接力说明及任务文档，写清完成编号、提交号、验证、风险和**唯一的**下一编号任务。
5. **同步**：提交后推送 `origin/dev`，再确认本地与远端相同（`ahead=0`、`behind=0`）。推送失败或尚未确认对齐时，只能称为“本地完成”，不得作为双电脑或新对话的接力起点。

通常迁移任务的下一轮是债务清单中紧邻的“选择”任务，选择任务的下一轮是该选择所固定的“迁移”任务；实际顺序始终以已更新的债务清单为准。


## 交接前：当前电脑

1. 确认在 `dev`：`git branch --show-current`。
2. 先检查并处理所有本地变更：`git status --short`。本轮已明确授权的前端、开发配置、文档和架构改动均必须
   以可审阅提交进入 Git；不要把 IDE、`target/`、`node_modules/`、日志、数据库文件或本机密钥加入提交。
3. 后端改动至少执行与任务匹配的专项/全量测试；代码任务按接力说明执行打包、
   `scripts/architecture-scan.sh --check-new` 与 `git diff --check`。纯文档任务无需构建，但仍执行适用门禁和空白检查。
4. 更新 `MoneyPOS-AI-Handoff.md`：当前任务、扫描计数、数据库测试命令、已完成提交、唯一下一最小任务；架构任务
   同时更新债务清单、实施台账和对应设计/选择文档。
5. 推送前读取远端：`git fetch origin`；若远端已有新提交，先以 `git pull --ff-only origin dev` 快进同步，
   不以强推覆盖另一台电脑的工作。
6. 推送并复核：`git push origin dev` 后执行 `git fetch origin`、`git status -sb`，确认 `dev` 与 `origin/dev`
   没有 ahead/behind。将推送后的提交号、当前任务编号、验证结果和唯一下一任务交给下一位接手者。

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

## 换对话 / AI 接力

新的 AI 对话也等同于一台新的接手电脑。开始前按以下顺序完整阅读：

1. `MoneyPOS-双电脑接力协议.md`；
2. `MoneyPOS-AI-Handoff.md`；
3. `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md`；
4. `MoneyPOS-Architecture-Debt-Backlog.md`；
5. 当前编号任务对应的选择或设计文档。

随后执行接手命令同步 `origin/dev`，检查工作区，并在改代码前复述当前编号、任务边界、上一轮验证结论与唯一下一编号。换对话不重开已闭环任务，也不在未推送历史上开始下一编号任务。最终交接必须给出：完成编号、提交号与 `origin/dev` 对齐状态、验证结果、风险/延期项，以及债务清单中的唯一下一最小任务。

## 数据库与验证隔离

- 每台电脑维护自己的 `money_pos` 开发库和 `money_pos_test` 测试库；代码同步不等于数据同步。
- 本机开发连接、首次安装、启动和需要同步测试数据的操作见 `WSL-双电脑开发测试环境指南.md`。
- 自动测试只能使用 `money_pos_test`，并从 `application-dev.yml` 读取可覆写的 `MONEY_DB_*` 凭据；不得把测试
  指向开发库或生产库。
- 结构变化必须以 Flyway 脚本提交；下一台电脑构建/启动后让 Flyway 升级自己的本地库。业务数据需要同步时，按
  WSL 指南显式导出/导入，并确认没有真实客户或生产数据。

## 当前接力资料

- 分支与远端：`dev` → `origin/dev`（SSH：`git@github.com:miozen/money-pos.git`）。
- 最新架构提交：以 `MoneyPOS-Architecture-Adjustment-Implementation-Ledger.md` 和 `git log --oneline` 为准。
- 当前最小任务：以 `MoneyPOS-AI-Handoff.md` 与 `MoneyPOS-Architecture-Debt-Backlog.md` 的“当前任务”为准。
- 共享 Entity 迁移前必须阅读对应选择文档；当前 AD-2.24 是选择任务，须先形成第十二个切片的选择文档。

## 冲突最小化约定

- 同一时刻只让一台电脑改动同一个文件或同一个最小架构任务。
- 开始前先拉取，结束后尽快提交和推送；提交保持单一目的，前端、环境文档和架构切片需要明确说明范围。
- `application-dev.yml` 可提交环境变量默认值，但禁止写入真实生产密码、访问令牌、私钥或数据库备份。
- 不能安全自动合并时，保留冲突证据并交由操作者选择，不猜测另一台电脑的意图。
- 未达到 `ahead=0`、`behind=0` 时，不在另一台电脑或新对话开始债务清单的下一编号任务；先恢复同一基线。
