# MoneyPOS 阶段 4：架构防回归清单

## 目标与边界

阶段 4 在阶段 2 的逻辑包边界和阶段 3 的运行时边界之上，建立**渐进式、可验证且不误伤既有债务**的架构防回归机制。

本阶段不重命名 URL、Controller、数据库表或 Flyway 脚本；不要求一次性迁移既有历史代码；不改变阶段 3 中延期的 Windows 嵌入式 MariaDB 和小票机硬件验收项。

规则的最终目标为：

1. Controller 不直接依赖 Mapper。
2. 一个 Feature 不直接依赖另一个 Feature 的 `ServiceImpl` 或 Mapper。
3. 跨 Feature 不能以对方内部 Entity 作为契约。
4. `platform` 不依赖 `feature`。

## 4.0 建立清单与基线扫描

- [x] 4.0.1 已将四条规则限定为“新增违规必须阻止、既有违规须有基线和迁移计划”的渐进式策略。规则只检查 `money-app-biz/src/main/java`；同一 Feature 内部的应用服务、领域服务和基础设施 Mapper 不属于跨 Feature 违规。
- [x] 4.0.2 已扫描 Controller 直连 Mapper：27 个 Controller 中有 7 个历史违规文件。它们是 `SysStrategyController`、`GmsBrandConfigController`、`GmsGoodsExcelController`、`GmsStockLogController`、`PosCouponRuleController`、`UmsMemberController` 和 `UmsMemberImportController`。这些文件是后续按业务切片迁移的基线，当前不修改。
- [x] 4.0.3 已扫描跨 Feature 的实现/持久层依赖：没有 Feature 直接导入另一个 Feature 的 `ServiceImpl` 或 Mapper；`platform` 也没有导入 `feature`。现有跨域调用使用服务接口或已建立的 TRADE facade，作为可接受的当前基线。
- [x] 4.0.4 已扫描 Entity 契约风险：62 个 Feature 文件仍导入旧共享包 `com.money.entity`。该包尚未按 Feature 物理归属，静态扫描无法可靠区分“本域实体”与“跨域实体”；在建立实体归属表前，不把此结果接入失败门禁，避免把既有兼容代码误判为新违规。
- [x] 4.0.5 已完成只读基线扫描并记录命令语义：Controller 文件以 `@(RestController|Controller)` 识别后检查 `import .*Mapper;`；跨 Feature 检查 `import com.money.feature.<other>.*(ServiceImpl|Mapper)`；平台边界检查 `platform` 下的 `import com.money.feature`。本项未改动业务代码、数据库或前端。

## 后续实施顺序

- [x] 4.1 已发布 `MoneyPOS-Architecture-Collaboration-Guide.md`：说明类型归属、允许的 Feature 服务接口/TRADE facade 调用、禁止的 Controller-Mapper 与跨 Feature 实现依赖、共享 Entity 的暂行兼容规则、历史基线限制及提交前自检。该指南不改变现有业务契约；下一项是 4.2 的 Entity 归属表。
- [ ] 4.2 建立 Entity 归属表和跨域场景 DTO 迁移优先级，先从已识别的 TRADE、GMS、UMS 调用面确定可替换类型。
- [ ] 4.3 将扫描实现为非阻断报告，并在报告稳定后只对“新增违规”启用门禁；7 个 Controller 基线和共享 Entity 债务必须显式豁免并可追踪。
- [ ] 4.4 选择一个低耦合 Controller-Mapper 违规作为首个迁移切片，改为调用本 Feature 的应用服务，并补充相应回归测试。
- [ ] 4.5 在每个迁移切片后更新基线、运行全量测试和打包；只有基线降为零或每项均有可执行豁免时，才评估将门禁升级为全量阻断。

## 4.0 验收结论

基线可作为后续变更的比较对象：新出现的 Controller-Mapper、跨 Feature `ServiceImpl` / Mapper 和 `platform → feature` 依赖均应被识别；共享 Entity 的跨域规则暂处于建模阶段。阶段 4 的下一最小任务是 **4.1 编写架构协作指南**，不应先直接改动这 7 个 Controller。
