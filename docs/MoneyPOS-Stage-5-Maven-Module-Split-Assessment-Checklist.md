# MoneyPOS 阶段 5：Maven 模块拆分评估清单

## 目标与边界

阶段 5 只评估是否值得、以及何时可以把 GMS、UMS、TRADE 从现有 `money-app-biz` 物理拆成 Maven 模块。它不是拆分授权：在明确通过依赖、循环、编译和测试四项门槛前，不移动源码、不改 POM、不改 Spring 扫描范围，也不改变路由、表或 Flyway。

本阶段沿用阶段 4 的规则：新增代码不得引入 Controller→Mapper、跨 Feature `ServiceImpl`/Mapper 或 `platform → feature` 违规；每次评估前执行 `bash scripts/architecture-scan.sh --check-new`。

## 5.0 模块与依赖基线

- [x] 5.0.1 已确认当前 Maven reactor：根 `qk-money` 下有 `qk-money-app`，其子模块仅为 `money-app-api`、`money-app-system` 和 `money-app-biz`。GMS、UMS、TRADE 都还是 `money-app-biz` 内的逻辑包，不存在可单独编译的业务 Feature 模块。
- [x] 5.0.2 已确认依赖方向：`money-app-biz → money-app-api`、`money-app-biz → money-app-system`，而 `money-app-api` 与 `money-app-system` 不依赖 `money-app-biz`。因此任何 Feature 模块若要成立，必须先明确它对 API、SYS、公共技术模块及其他 Feature 契约模块的依赖，不能反向让 API 或 SYS 依赖业务模块。
- [x] 5.0.3 已记录候选规模：`feature.gms` 36 个 Java 文件，`feature.ums` 14 个，`feature.trade` 43 个；它们仍分别有 22/10/28 个共享 Entity 导入、11/7/15 个 Mapper 导入。这些本域持久化访问并不自动否决模块化，但说明不能把 Feature 模块简单设为只含 Controller 或只含 Service。
- [x] 5.0.4 已扫描直接 Feature 源码依赖：UMS→GMS 为 3 个文件，UMS→TRADE 为 1 个；TRADE→GMS 为 5 个，TRADE→UMS 为 5 个；GMS 当前不直接导入其余候选 Feature。UMS 与 TRADE 已形成双向源码依赖，当前不可作为相互独立的 Maven 业务模块直接拆分。
- [x] 5.0.5 已作出基线结论：阶段 5 当前不创建任何新的 Maven 业务模块。GMS 在三者中依赖方向最单向，但其被 UMS/TRADE 消费且仍与共享 API/Entity/Mapper 层耦合；在先完成契约提取和独立编译收益证明前，也不进行物理移动。

## 拆分准入门槛

以下四项必须针对某一个候选 Feature 同时满足，才能提议实际模块化：

1. **稳定契约**：跨 Feature 调用只经接口、facade 或场景 DTO；不以对方实现、Mapper 或内部 Entity 为契约。
2. **无循环依赖**：候选模块的 Maven 依赖图为有向无环图；共享的调用模型应下沉到中立 API/契约模块，而不是让两个业务模块互相依赖。
3. **独立构建价值**：可以用 `mvn -pl <candidate> -am test-compile` 或相称测试真实验证该模块，且比全量构建更有诊断或执行价值。
4. **运行时可装配**：Spring Boot 启动模块仍能扫描 Bean、Mapper、配置和 Flyway；集成测试证明关键事务与 Web 路由未退化。

## 后续评估顺序

- [x] 5.1 已盘点并分类 UMS↔TRADE 的双向依赖：识别 3 条只读边、POS 聚合查询和 2 条交易写侧协调；TRADE 还直接使用 UMS Entity/Mapper/IService，不能仅移动服务接口。设计以中立契约模块承载 Entity-free DTO/接口，UMS/TRADE 分别实现对应 port，组合层注入实现；不授权直接拆分。详见 `MoneyPOS-Stage5-Ums-Trade-Cycle-Inventory.md`。下一最小任务是评估 GMS 单向候选边界及中立契约模块的最小依赖集。
- [x] 5.2 已评估 GMS 单向候选边界与中立契约最小集：GMS 没有直接依赖 UMS/TRADE，是方向上最适合作为首候选的模块；但服务仍经 `IService<Entity>` 暴露持久化类型、内部仍依赖共享 API/SYS、测试仍启动完整应用，独立编译收益未被证明。因此暂不创建 `money-app-gms`。未来契约模块只能容纳 Entity-free DTO/port 与 JDK 类型。详见 `MoneyPOS-Stage5-Gms-Module-Candidate-Assessment.md`。下一最小任务是汇总目标 Maven 依赖图并形成“拆分 / 暂不拆分”决策。
- [x] 5.3 已汇总 Maven 依赖图并作出正式“暂不拆分”决策：UMS↔TRADE 的循环、Entity/Mapper/IService 泄露、缺乏独立构建收益和未验证的运行时装配均未达到准入门槛；GMS 仅在依赖方向上具备候选资格。不会提交 POM 或源码移动计划。详见 `MoneyPOS-Stage5-Maven-Module-Split-Decision.md`。下一最小任务是记录暂不拆分的证据并关闭阶段 5 评估。
- [ ] 5.4 若无安全拆分候选，记录“暂不拆分”的证据并关闭阶段 5；若存在候选，则先建立独立编译/测试验证，再提出实施切片。

## 当前风险与决策

当前最大风险不是 POM 语法，而是 UMS↔TRADE 的循环：直接把两者拆成相互依赖的 JAR 会使 Maven reactor 无法拓扑排序。阶段 4 已消除实现层/Mapper 层违规，但这不等于所有服务接口已经具备模块契约稳定性。

因此下一最小任务是 **5.1：逐文件盘点 UMS↔TRADE 双向依赖并给出最小破环契约设计**。在该设计完成前，不创建空壳 Maven 模块，也不修改现有构建。
