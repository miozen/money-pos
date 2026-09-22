# MoneyPOS AD-5：CI 架构门禁接入设计

## 决策

采用**独立的 Linux 静态门禁工作流**，而不是把架构扫描附加到现有 Windows EXE 打包工作流。该工作流只执行：

```bash
bash scripts/test-architecture-scan.sh
bash scripts/architecture-scan.sh --check-new
```

它不运行 Maven、Node、Flyway、Spring Boot、MariaDB、Electron 或打包下载，也不读取数据库密码、发布凭据或
其他密钥。现有 `.github/workflows/build-exe.yml` 保持为仅 `main` 推送/手动触发的发布构建，不在 AD-5.1 改动。

## 当前事实

| 项目 | 事实 | 设计影响 |
| --- | --- | --- |
| 现有 CI | 仅有 `Build WanXiangPOS EXE Installer`；触发 `main` push 和手动执行，Windows runner，Maven 使用 `-DskipTests`，并下载 JRE/MariaDB、构建前端和 EXE。 | 过慢且有外部下载/打包不确定性，不能作为 `dev` 每次架构反馈。 |
| 架构门禁 | `scripts/architecture-scan.sh --check-new` 为静态 Bash/Ripgrep 扫描；当前返回 0。 | 可在无数据库/无 Maven 的 Linux runner 上直接执行。 |
| 失败语义 | 仅新增 Controller→Mapper、跨 Feature 实现/Mapper、`platform → feature`、新/未登记共享 Entity 或新通配符才返回 1；默认无参数仅报告。 | CI 必须传 `--check-new`，不能调用默认报告模式作为通过条件。 |
| 夹具 | `scripts/test-architecture-scan.sh` 验证允许本域、拒绝退休/未登记 Entity 与新通配符；文件模式为 `100644`。 | CI 必须以 `bash scripts/test-architecture-scan.sh` 调用，不能直接执行文件。 |
| 当前基线 | 生产扫描当前全部为 0，所有权/桥/通配符登记均为 0；脚本仍保留 additions-only 规则以阻止回归。 | 不把“当前为 0”改造成扫描默认模式或修改基线。 |
| 分支流程 | 开发分支为 `dev`，`main` 是发布打包触发分支。 | 门禁应覆盖 `dev` push、以 `dev` 或 `main` 为目标的 PR，以及手动重跑。 |

## AD-5.1 拟实施的工作流

新增一个独立文件（建议名 `.github/workflows/architecture-gate.yml`），其内容范围固定如下；本 AD-5 不创建该文件。

```yaml
name: Architecture Gate

on:
  push:
    branches: [dev]
  pull_request:
    branches: [dev, main]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: architecture-gate-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  additions-only:
    name: additions-only
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
      - name: Install scan tool
        run: |
          sudo apt-get update
          sudo apt-get install --yes ripgrep
      - name: Verify scan tools
        run: |
          bash --version
          rg --version
      - name: Verify architecture scan fixtures
        run: bash scripts/test-architecture-scan.sh
      - name: Enforce additions-only architecture gate
        run: bash scripts/architecture-scan.sh --check-new
```

`pull_request` 不使用 `pull_request_target`，不 checkout 外部 PR 的密钥上下文，也没有 write token。`contents: read`
保持最小权限。超时和并发取消仅用于节省排队资源，不改变任何业务构建结果。

## 失败与基线治理

- `additions-only` 的非零退出必须直接失败；不得使用 `continue-on-error`，不得吞没 `rg`、夹具或扫描错误。
- 输出应保留在 Actions 日志中，便于看到具体文件和违规类别；无需上传产物、连接数据库或发送通知。
- 允许清单仍由脚本与 `scripts/architecture-baseline/` 中的已提交数据表达。修改扫描规则或基线不是普通业务提交：
  必须有独立任务/审查说明、更新夹具，并在 PR 中明确为什么新的例外不是应当通过 DTO/契约消除的违规。
- 本阶段不自动阻止任何基线文件的改动，也不引入 `CODEOWNERS` 或分支保护规则；这是仓库治理权限问题，需由
  管理员在 AD-5.1 验证后决定。不能用一个未经验证的 diff 规则替代现有、已特征化的脚本语义。

## 与发布、测试和数据库的边界

- 架构门禁不是完整 CI：现有 EXE 工作流仍跳过 Maven 测试，且需要网络下载。数据库集成回归继续按本地
  `money_pos_test` 规则执行，未来若要上 CI 须另立数据库/测试环境设计。
- 门禁不执行 `mvn package` 或前端构建；因此其失败只说明静态架构规则或扫描夹具失败，不能误报为发布失败。
- `main` 发布构建不会因本设计被更名、重触发或改变产物。待独立实施后，分支保护可要求显示检查
  `Architecture Gate / additions-only` 通过后才合并/更新受保护分支；实际设置前须由仓库管理员在 GitHub 设置页确认。

## AD-5.1 实施与验收

1. 只新增该一个工作流文件；不改 `build-exe.yml`、Maven POM、业务源码、扫描逻辑或基线。
2. 本地执行 `bash -n scripts/architecture-scan.sh scripts/test-architecture-scan.sh`、夹具和 `--check-new`；YAML 语法按可用工具验证。
3. 推送至 `dev` 后，在 Actions 确认 `Architecture Gate / additions-only` 运行且通过；创建或更新一个以 `dev`/`main`
   为目标的 PR，确认同一门禁运行。
4. 在临时、不可合并的验证分支增加一个已知会触发的违规，确认 CI 红灯并显示文件；丢弃该验证分支，不能把违规或
   基线变更带回 `dev`。
5. 由仓库管理员决定是否把该检查设为分支保护 required status check；设置后再次确认普通 `dev` 提交与 PR 的行为。

## 风险与延期

- GitHub Actions 当前只存在发布工作流；仓库分支保护、PR 使用习惯和管理员权限无法从本地源码推断，故不在 AD-5
  擅自配置。
- 首次远端执行确认 `ubuntu-latest` 未预装 `rg`；AD-5.1 因此显式通过 APT 安装 `ripgrep`，再输出版本。该下载仅为
  门禁所需扫描工具，不下载应用 JRE/MariaDB、不构建发布产物，也不静默降级扫描。
- 门禁保护的是架构新增回归，不测数据库、交易、启动性能或打包。这些风险继续由专项回归、ENV-1/ENV-2 和后续
  CI 测试环境设计承担。

## AD-5.1 远端验收记录

- `fccaf5a` 首次推送后，GitHub runner 在工具检查阶段报告 `rg: command not found`（退出码 127）；未执行夹具或
  生产扫描。`47fb359` 显式安装 `ripgrep` 后，`Architecture Gate / additions-only` 在 `dev` 推送中全部通过。
- 临时分支 `codex/ad5-1-negative-gate` 的提交 `ad7f933` 仅新增
  `Ad51GateViolationController.java`，故意触发 Controller→Mapper 规则。其目标为 `dev` 的 PR 触发门禁并以退出码 1
  红灯，日志精确报告该文件；证明 PR 触发和失败传播均生效。
- 该 PR 未合并，远端临时分支已删除，本地临时分支也已删除；`dev...origin/dev` 再次对齐。分支保护 required check
  仍是仓库管理员可选的后续设置，不是本次实现的阻塞项。
