# MoneyPOS 阶段 3 运行时能力边界执行清单

## 阶段目标

阶段 3 在阶段 2 的逻辑业务包边界稳定后，整理桌面单机版的**运行时能力**：工作区初始化、内嵌 MariaDB 守护和本地文件路径。目标是让业务 Feature 不再承担这些基础设施的生命周期与路径规则，同时保持现有桌面版启动、数据库和文件语义不变。

本阶段不是业务功能重构，也不处理阶段 2 已记录的 FIN 查询兼容风险、HOME 快照并发风险或共享 Mapper 收敛；这些进入后续专门切片处理。

## 使用规则

- 每个切片先完成依赖盘点和现有行为特征测试，再移动或抽取代码；不得以“编译通过”替代桌面运行时验证。
- 运行时能力放入独立的 `platform`/runtime 包；`platform` 不得依赖 `feature.*`。业务 Feature 只能通过稳定配置或能力接口使用运行时能力。
- 不改变 Controller、URL、前端 API、表名、Flyway、业务事务边界、MariaDB 数据目录格式、默认端口或已有启动参数的含义。
- 每个已完成切片独立 Git 提交，并在实施台账记录：变更范围、验证结果、回滚点、残余风险和下一步。
- 只在本地 `dev` 开发；未获用户明确要求不得推送，也不得合并或推送 `main`。不得将用户已有的前端或本地配置未提交改动纳入本阶段提交。
- 发现 Windows 打包版、Electron、真实 MariaDB 或真实打印机所需条件不足时，记录为环境验收项；不得用猜测性改动替代验证。

## 阶段完成门槛

- [ ] 工作区寻址、目录准备、应用配置注入和内嵌 MariaDB 生命周期具有明确的运行时归属，且生产业务 Feature 不直接依赖其实现细节。
- [ ] 打包版通过既有 `--app.home` 或等价嵌入式开关启动时，仍能创建/复用既有数据目录、识别关联 MariaDB 实例并注入原有数据源配置。
- [ ] IDE/WSL 开发模式不尝试查找或启动 Windows `mysqld.exe`，继续使用 `application-dev.yml` 中配置的外部数据库。
- [ ] 工作区和 MariaDB 的正常启动、首次初始化、已有实例复用、端口冲突拒绝、关闭流程均有可执行的自动化或环境验收记录。
- [ ] 本地文件路径（资产、日志、备份、数据库数据）有单一能力入口；调用方不再自行拼接与运行时工作区相关的路径。
- [ ] 全量 `mvn test`、`mvn package`、阶段 0 套件及适用的桌面/开发模式启动验证通过；每个切片可由独立提交回滚。
- [ ] 小票打印硬件验收仍作为可追溯延期项保留，不因本阶段未具备打印机而阻塞本阶段其他工作。

## 3.0 基线、范围与提交纪律

- [x] 3.0.1 已记录：本地 `dev` 比 `origin/dev` 领先 22 个本地提交；现有前端、本地 `QkMoneyApplication.java`、`application-dev.yml` 与 WSL 文档改动保持不纳入阶段 3。当前启动模式为桌面嵌入式（`--app.home` 或 `money.workspace.embedded=true`）、IDE/WSL 外部数据库和 test profile 三种。
- [x] 3.0.2 已盘点 `AppWorkspace`、`WorkspaceEnv`、`AppConfigInjector`、`MariaDbGuardian`、`QkMoneyApplication` 及其生产调用方。嵌入式模式顺序为目录准备、MariaDB 守护、配置注入；IDE/WSL 模式跳过此顺序，使用开发配置；测试通过独立 `application-test.yml` 运行。
- [x] 3.0.3 已新增并通过 `RuntimeCapabilityCharacterizationTest`（4 tests, 0 failures, 0 errors）：覆盖嵌入式启动判定、显式数据目录与四类运行时目录、数据源/资产配置注入，以及既有 MariaDB 端口和库名契约；测试不启动真实 Windows MariaDB。
- [x] 3.0.4 已建立依赖基线：`workspace` 由启动类及备份服务/任务使用；本地文件由 `LocalFileService` 和 `StorageWebConfig` 通过 `local.bucket` 使用；备份直接依赖工作区和 MariaDB 守护；打印由 FIN/TRADE Controller 使用；WebSocket 配置和 `PosSyncServer` 独立存在。发现备份任务使用 `app.home/backups`、备份服务使用 `app.data/backups` 的既有目录不一致，留待 3.3/3.4 专项处理。本阶段仅处理工作区、MariaDB 守护和本地文件能力。

## 3.1 工作区初始化能力切片

当前候选类型：`AppWorkspace`、`WorkspaceEnv`、`AppConfigInjector`。

- [x] 3.1.1 已盘点工作区输入和输出：`app.data` 为最高优先级数据根目录，安装根目录由运行时类位置推导；输出为 `assets`、`logs`、`backups`、`db_data`、`app.home`、`app.data` 及数据源/资产配置。兼容调用方为启动类、MariaDB 守护、备份服务/任务和静态资源映射。
- [x] 3.1.2 已定义公开边界 `com.money.platform.runtime.workspace.RuntimeWorkspace`：调用方只能获取应用/数据目录或请求目录准备；不再复现桌面路径推导。备份服务与任务已改为使用该公开入口。
- [x] 3.1.3 已将工作区初始化、环境解析和配置注入迁入 `platform.runtime.workspace`。旧 `AppWorkspace`、`WorkspaceEnv` 和 `AppConfigInjector` 保留为标记废弃的兼容桥；未改动用户未提交的启动类，`app.home`、`app.data` 与既有目录名称保持不变。
- [x] 3.1.4 已通过 `RuntimeCapabilityCharacterizationTest` 验证显式临时数据根目录首次创建 `assets`、`logs`、`backups`、`db_data`，并验证旧 `WorkspaceEnv` 委托新边界（5 tests, 0 failures, 0 errors）；测试不写入 IDE/WSL 工程目录。
- [x] 3.1.5 已完成编译、特征测试与真实开发模式 Spring 上下文验证：聚合 `mvn -pl qk-money-app/money-app-biz -am package -DskipTests` 通过；`RuntimeCapabilityCharacterizationTest` 为 5 tests, 0 failures, 0 errors；后端以 WSL 本机 MariaDB `money_pos` 成功启动，Flyway 无待迁移，`/money-pos/actuator/health` 返回 `UP`（含数据库 `SELECT 1`）。本切片以独立本地提交收口；未推送。

## 3.2 内嵌 MariaDB 守护能力切片

当前候选类型：`MariaDbGuardian` 及工作区对它的启动编排。

- [x] 3.2.1 已盘点 MariaDB 守护的状态与外部契约：固定端口 `9102`、库名 `money_pos`、`.sys_secret.key`、`db_data`、`.wx_meta`、首次初始化、通过 `@@datadir` 的实例身份校验、JVM 关闭钩子和启动前数据源配置。
- [x] 3.2.2 已新增 `EmbeddedMariaDbGuardianCharacterizationTest`（2 tests）：覆盖受管实例目录的大小写/分隔符归一化、非关联目录拒绝判定，以及真实临时本机端口的占用识别；既有启动模式测试覆盖开发模式不启动 Windows 引擎。真实 `mysqld.exe` 启动仍仅在 Windows 打包环境作环境验收。
- [x] 3.2.3 已将 MariaDB 进程生命周期和身份校验迁入 `com.money.platform.runtime.database.EmbeddedMariaDbGuardian`；工作区、配置注入和备份服务已改用新公开契约。旧 `MariaDbGuardian` 保留为标记废弃的兼容桥，业务 Feature 未直接依赖守护实现。
- [x] 3.2.4 已保持安全与恢复语义：密码文件和 `.wx_meta` 仍位于 `app.data`，首次空库仍创建 `money_pos`，既有数据目录仍以 `@@datadir` 识别；未改数据库 schema、端口、库名或业务连接参数。
- [ ] 3.2.5 已验证 IDE/WSL 外部数据库路径：当前 JAR 启动后健康检查为 `UP`，连接 `127.0.0.1:3306/money_pos`，未调用 Windows 引擎。Windows 打包版待验收：在含 `mariadb/bin/mysqld.exe` 和 `mysql_install_db.exe` 的 Windows 安装目录，以既有 `--app.home` 或 `-Dmoney.workspace.embedded=true` 启动；确认 `app.data/db_data` 首次初始化/关联实例复用及端口冲突拒绝。该环境当前不可用，故本项不标记完成。
- [x] 3.2.6 已完成聚合编译与打包、7 项运行时特征测试和 WSL Spring 健康检查；本切片以独立本地提交收口，台账记录回滚点与 Windows 环境差异。

## 3.3 本地文件能力切片

范围仅包括工作区下的文件定位与目录访问；备份业务流程本身暂不迁移。

- [ ] 3.3.1 扫描资产、日志、备份、导入导出和数据库数据的路径拼接点，标注运行时路径与业务文件名/格式两类责任。
- [ ] 3.3.2 定义本地文件能力的最小公开接口或配置入口：提供受控的资产、日志、备份和数据库数据位置，不把 `File` 路径规则散落到业务服务中。
- [ ] 3.3.3 迁移与工作区直接相关的路径解析调用；业务侧保留文件格式、导入导出和备份业务语义，禁止在本切片重写文件协议。
- [ ] 3.3.4 为目录创建、路径归属和异常路径补测试；确认 Windows 路径与 WSL/IDE 开发路径不会互相覆盖或误删。
- [ ] 3.3.5 完成编译、相关文件功能回归和独立提交；实施台账记录剩余直接文件访问点及其保留理由。

## 3.4 明确延期的运行时能力

以下能力在本阶段仅完成盘点、边界说明和验收准备，除非后续形成独立切片，否则不迁移实现。

- [ ] 3.4.1 备份与恢复：盘点 `SysBackupService`、`SysBackupTask`、下载接口和 MariaDB 工具调用；保留现有备份文件格式、恢复流程和权限语义，不与 3.3 混合迁移。
- [ ] 3.4.2 小票打印：盘点 `PosPrinterService` 的调用面及硬件依赖；继续保留阶段 2 的“无小票机、硬件验收延期”记录。取得兼容打印机后，执行收银与退款小票各一次的实际打印验收。
- [ ] 3.4.3 POS WebSocket：盘点 `WebSocketConfig`、`PosSyncServer` 与前端握手约定；保留现有业务同步语义，不在本阶段改协议或端点。
- [ ] 3.4.4 Electron 主进程：确认 `main.cjs` 不在阶段 3 修改范围；只记录它向后端传递运行时参数的现有契约，任何主进程改动另立任务。

## 3.5 阶段收口验证

- [ ] 3.5.1 运行依赖扫描：`platform`/运行时包不依赖 `feature.*`；业务 Feature 不直接依赖运行时实现类，保留的兼容桥均有台账理由。
- [ ] 3.5.2 运行工作区、MariaDB 和文件能力的目标特征测试，以及阶段 0 `CheckoutIntegrationTest`；记录测试数量和结果。
- [ ] 3.5.3 运行全量 `mvn test` 与 `mvn package`；确认打包产物不因包调整缺失运行时类或资源。
- [ ] 3.5.4 在可用环境完成开发模式启动和桌面嵌入式启动验收；分别记录访问方式、数据库来源、目录位置和结果。桌面环境不可用时保留待验收项，不阻塞已验证的开发模式。
- [ ] 3.5.5 更新实施台账，列出已完成能力、独立提交号、延期硬件项、平台环境限制和阶段 4 的输入；确认未推送 `dev`、未触及 `main`。

## 阶段 4 的预期输入

阶段 3 收口后，阶段 4 才添加最小可执行的架构防回归规则：Controller 不直连 Mapper、Feature 不依赖其他 Feature 的 `ServiceImpl` 或 Mapper、跨 Feature 不以内部 Entity 作为契约，以及 `platform` 不依赖 `feature`。规则落地前应先用本清单留下的依赖扫描结果作为基线，避免把既有兼容债务误判为新违规。
