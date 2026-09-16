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

- [x] 3.3.1 已扫描：运行时根目录只在工作区、MariaDB 守护、备份服务/定时任务和 `local.bucket` 注入处出现；上传服务和静态资源映射只消费 `local.bucket`。备份 ZIP 文件名、SQL/Manifest 格式、导入导出内容与删除策略属于业务责任，保留给 3.4。
- [x] 3.3.2 已定义 `com.money.platform.runtime.file.RuntimeFileStorage`：统一提供资产、日志、备份、数据库数据目录，以及运行时根目录文件和兼容 `local.bucket` 的资产物理路径。
- [x] 3.3.3 已迁移工作区直接相关的路径定位：目录准备、数据库数据/元数据文件、数据源资产路径、备份服务的资产/备份根目录、夜间任务的备份清理均使用 `RuntimeFileStorage`。未改变备份包格式、恢复步骤、上传对象键、静态 URL、导入导出或删除策略。
- [x] 3.3.4 已新增 `RuntimeFileStorageTest`：在隔离临时数据根目录验证资产、日志、备份、数据库数据和元数据文件均归属于该根目录，并保持 `local.bucket` 的末尾分隔符契约。IDE/WSL 外部开发配置继续通过既有 `local.bucket` 生效，不会触发 Windows 桌面目录探测或互相覆盖。
- [x] 3.3.5 已完成聚合编译、8 项运行时特征测试和独立本地提交；台账记录了仍保留的直接文件访问均为备份 ZIP/SQL/Manifest 业务格式和资源映射实现，而非运行时根目录规则。

## 3.4 明确延期的运行时能力

以下能力在本阶段仅完成盘点、边界说明和验收准备，除非后续形成独立切片，否则不迁移实现。

- [x] 3.4.1 已盘点备份与恢复：`SysBackupController` 提供 `/sys/backup/stream`、`/export`、`/restore`；`SysBackupService` 生成含 SQL、Manifest 与资产的 ZIP，恢复前创建保护备份并经影子库校验/原子表切换；`SysBackupTask` 每日调用同一服务并只清理旧自动备份。MariaDB 的 `mysqldump`/`mysql` 调用、ZIP/Manifest 格式、SSE、下载后临时文件清理、恢复确认和权限语义均未改动，后续若需改动另立备份专项。
- [ ] 3.4.2 已盘点小票打印：`PosPrinterService` 经 `PrintServiceLookup.lookupDefaultPrintService()` 向默认打印机发送 ESC/POS 字节；TRADE `/oms-order/hardware/print` 触发收银/钱箱小票，FIN `/finance/shift-handover/print` 触发交接班小票，配置来自 `/system/config/print`。当前未发现退款流程调用打印服务，故“退款小票”尚无可验收入口，须另立需求/实现切片；当前无兼容小票机，收银及交接班打印同样保留硬件延期，不能以接口成功代替。
- [x] 3.4.3 已盘点 POS WebSocket：`WebSocketConfig` 注册 `ServerEndpointExporter`，`PosSyncServer` 使用固定端点 `/ws/pos-sync` 和线程安全会话集合，接收文本后广播。收银端 `useDisplaySync` 发送 `IDLE`、`CASHIER_UPDATE`、`CHECKOUT_OPEN`、`PAY_SUCCESS` JSON；客显 `usePosSync` 按此状态和 `cart`、`pAmount`、`member`、`payment` 字段渲染并重连。端点、端口 `9101`、上下文 `/money-pos` 和消息字段均未改动。
- [x] 3.4.4 已确认 Electron `money-pos-web/main.cjs` 不在阶段 3 修改范围：打包版从安装目录选择 JRE/JAR，以 `java -jar ... --app.home=<APP_ROOT>` 启动后端，并轮询既有健康接口后创建主/客显窗口；退出时终止子 Java 进程。任何 Electron、JAR 命名、窗口或启动参数改动均须另立任务。

## 3.5 阶段收口验证

- [x] 3.5.1 已完成运行依赖扫描：`platform.runtime` 不依赖 `feature.*`，Feature 未直接导入 `platform.runtime`；旧 `workspace` 桥仅委托新运行时边界，兼容理由已在台账记录。另发现 7 个历史 Controller 直接导入 Mapper，作为阶段 4 的既有基线，不在阶段 3 扩大处理。
- [ ] 3.5.2 运行工作区、MariaDB 和文件能力的目标特征测试，以及阶段 0 `CheckoutIntegrationTest`；已通过 8 项运行时特征测试，但 `CheckoutIntegrationTest` 需要独立 `money_pos_test` 数据库，当前应用账号没有创建/访问权限，待测试库授权后执行。
- [ ] 3.5.3 运行全量 `mvn test` 与 `mvn package`；全量 `mvn package -DskipTests` 已通过，`mvn test` 待 3.5.2 的独立测试库就绪后执行，确认打包产物不因包调整缺失运行时类或资源。
- [ ] 3.5.4 在可用环境完成开发模式启动和桌面嵌入式启动验收；已完成 WSL 开发模式：`http://127.0.0.1:9101/money-pos/actuator/health` 为 `UP`，数据库为外部 `127.0.0.1:3306/money_pos`。Windows 桌面嵌入式仍待具备打包目录、Windows MariaDB 引擎和可用设备的环境验收，不把本项标记完成。
- [ ] 3.5.5 更新实施台账，列出已完成能力、独立提交号、延期硬件项、平台环境限制和阶段 4 的输入；确认未推送 `dev`、未触及 `main`。待 3.5.2 与 3.5.3 的测试库验证完成后收口。

## 阶段 4 的预期输入

阶段 3 收口后，阶段 4 才添加最小可执行的架构防回归规则：Controller 不直连 Mapper、Feature 不依赖其他 Feature 的 `ServiceImpl` 或 Mapper、跨 Feature 不以内部 Entity 作为契约，以及 `platform` 不依赖 `feature`。规则落地前应先用本清单留下的依赖扫描结果作为基线，避免把既有兼容债务误判为新违规。
