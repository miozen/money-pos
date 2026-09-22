# AD-4：Maven 物理模块化重新评估

## 范围与结论

本轮只读审查当前 Maven 结构、Feature 依赖、Mapper 归属、核心事务、Spring/MyBatis/Flyway 装配和测试独立性；
未创建 POM、未移动源码、Mapper 或资源，未改业务代码、路由、表、Flyway 或事务。

**结论：继续维持 `money-app-biz` 作为单一业务 Maven 模块。**

AD-2 已完成所有共享 Entity 的物理归属收敛，P1/P2/AD-3 已将已盘点跨域场景改为 API 中立契约。它们使
源码边界达到拆分的必要前提，但尚不足以证明 Maven 拆分带来独立构建/测试收益，或能以可接受风险完成
Spring/MyBatis/Flyway 组合验证。当前不应拆成很多小模块，也不应仅为“包名整洁”创建
`money-app-gms`、`money-app-ums`、`money-app-trade` 或新的 contract 模块。

原阶段 5 的“暂不拆分”结论曾基于 UMS↔TRADE 的直接依赖和共享 Entity；这些事实已被后续 P1/P2/AD-2/AD-3
迁移替代。本评估重新取证后仍得出“暂不拆分”，但理由更新为：持久化/启动装配集中、测试不独立和收益未证明，
而不是旧的跨 Feature 源码循环。

## 当前实测基线

| 维度 | 证据 | 结论 |
| --- | --- | --- |
| Maven 应用结构 | `qk-money-app` 只有 `money-app-api`、`money-app-system`、`money-app-biz` 三个子模块；启动类和 Spring Boot 插件都在 `money-app-biz`。 | GMS、UMS、TRADE、FIN、HOME、SYS 仍是同一业务 JAR 内的逻辑 Feature。 |
| Feature 直接依赖 | 对 `feature.gms/ums/trade/fin/home` 的跨 Feature production import 重新扫描为 0；架构扫描同时报告 Controller→Mapper、跨 Feature 实现/Mapper、`platform → feature`、共享 Entity import、登记、桥和通配符均为 0。 | 源码边界和契约方向已经稳定；不存在已知的直接 Feature Maven 循环。 |
| 中立契约 | GMS/UMS/TRADE 分别有 10/13/13 个 `Query`、`CommandHandler` 或 command 实现类；API 中已有商品、会员和交易的 Entity-free snapshot/port。 | 已具备单进程协作边界，但这些接口尚未被独立 JAR 的编译/测试证明。 |
| Mapper 归属 | 26 个 Mapper 接口仍在 `com.money.mapper`；GMS/UMS/TRADE Feature 中分别有 17/19/18 个源码文件直接导入该集中包。 | Entity 已归属 Feature，但持久化访问仍是集中兼容基础设施，不能把 Feature 目录直接剪切为 Maven 模块。 |
| MyBatis 装配 | `MybatisConfig` 显式扫描 `com.money.mapper` 及 FIN/GMS/TRADE 的 Feature mapper 包；多数遗留 Mapper 依靠这项集中扫描。 | 拆分需同时重新安排 Mapper package、扫描边界和 Bean 唯一性，不能只改 POM。 |
| Flyway/运行时装配 | `money-app-biz` 依赖 Flyway，`application-money.yml` 固定 `classpath:db/migration`，其中有四个全局迁移脚本；启动类也在该模块。 | 目前是一套单进程、单数据源、全局迁移的应用组合，尚无按 Feature 迁移/启动验证。 |
| 核心事务 | `CheckoutOrchestrator` 的一个 `@Transactional` 事务顺序执行建单、库存命令、会员资产命令和支付；退款也在 TRADE 边界事务内协调库存与资产恢复。 | 契约可跨 JAR，但任何拆分必须证明同一 DataSource、事务代理、异常传播和 Bean 装配仍保持现有原子性。 |
| 测试独立性 | 58 个测试类中 53 个使用 `@SpringBootTest`，以 `QkMoneyApplication`、MyBatis、Flyway 和隔离 MariaDB 共同启动；只有 5 个是非完整 Boot 的运行时/定时任务测试。 | 当前回归很好地保护整应用，但没有任何 Feature 可独立 `test-compile`/测试的收益证据。 |

当前 Feature 规模为 GMS 56、UMS 35、TRADE 59、FIN 16、HOME 14、SYS 4 个 Java 源文件。这里的数量说明
“一个实体一个 Maven 模块”没有工程价值；也说明若未来真有组织、发布或构建时长需求，应以完整业务边界为候选，
而不是按类或 Mapper 细碎拆分。

## 三种方案对比

| 方案 | 收益 | 代价和风险 | 当前结论 |
| --- | --- | --- | --- |
| A. 维持现状：单 `money-app-biz` + 逻辑 Feature + API 契约 | 保持已验证的单进程事务、完整 Boot/Flyway/MyBatis 装配和隔离库回归；现有 additions-only 门禁已阻止边界倒退。 | 业务 JAR 较大，Mapper 集中包降低物理所有权可见性；不能获得 Feature 级增量构建。 | **推荐。** 当前没有可量化的拆分收益，风险最低。 |
| B. 仅整理 Mapper 归属：把集中 Mapper 按 Feature 移入各自 persistence 包，但仍保留一个 biz 模块 | 使 Entity、Mapper、应用服务的物理位置一致，为将来评估提供更清晰的输入。 | 需迁移 26 个接口和所有 import，重做 `@MapperScan`；分析型 Mapper 的跨表职责、SYS/HOME mapper 及 Bean 名称需逐项决定。它本身不带来独立编译或发布收益。 | **不作为当前任务。** 仅当出现明确的维护痛点或获准为未来模块化做独立、逐 Feature 的低风险准备时再设计。 |
| C. 按 Feature 拆 Maven：GMS/UMS/TRADE（及可能的 boot/contract）模块 | 理论上可得到更强编译隔离、清晰所有权和未来独立发布的选择。 | 同时涉及 Mapper、扫描、Flyway、启动模块、依赖图、测试夹具及跨域事务；53 个完整 Boot 测试不能证明模块独立性。拆分 JAR 不会天然保留结账/退款的事务组合。 | **不批准。** 尚不具备独立构建价值和拆后运行时证据。 |

## 对核心装配与事务的影响判断

当前 `QkMoneyApplication` 位于 `money-app-biz`，`@SpringBootApplication` 从 `com.money` 统一扫描。`MybatisConfig`
把集中 `com.money.mapper` 与有限 Feature mapper 包硬编码为扫描根；Flyway 则从业务模块 classpath 加载所有
`db/migration` 脚本。因而“仅创建依赖 JAR”不是隔离：它仍要明确谁拥有启动、Mapper 注册、迁移发现和组合配置。

结账展示了拆分时最敏感的边界：TRADE 的 `CheckoutOrchestrator` 开启事务，随后通过 `GoodsStockFacade` 调用
GMS 的库存 command handler，并通过 `MemberAssetFacade` 调用 UMS 的会员资产 command handler。当前三者在
同一 Spring 容器、同一数据源和同一事务传播链中工作。未来可将它们置于不同 JAR，但必须先以拆后应用证明：
Bean 唯一、Mapper 注册完整、Flyway 只执行一次、事务代理传播和回滚语义不变。没有这组证明，物理拆分会给
最关键的结账/退款路径引入不必要风险。

## 重新开启拆分评估的客观条件

只有以下条件同时出现，才应另立设计任务，而不是直接改 POM：

1. 有明确收益证据，例如团队所有权、独立交付需求或可测得的构建/测试瓶颈；“目录更整齐”不构成理由。
2. 先为一个完整候选 Feature 建立不依赖全量 Boot 的 `test-compile` 或相称测试，并证明其诊断价值。
3. 明确 Mapper 的逐项所有者、分析跨表 Mapper 的归属，以及新的 `@MapperScan` 与 Bean 命名策略。
4. 设计唯一 boot/composition 模块，验证 `classpath:db/migration` 的顺序、一次性执行和回滚方案。
5. 以拆后集成回归证明结账、部分/整单退款、库存恢复、会员资产恢复、租户拦截器和 HTTP 路由均保持行为一致。

在这些条件满足前，继续以 API 契约和 `scripts/architecture-scan.sh --check-new` 维护边界。下一架构工作应转向
AD-5：评估如何把已经稳定的 additions-only 门禁纳入 CI；这比制造尚无独立价值的 Maven 子模块更直接地降低回归风险。
