# MoneyPOS 架构协作指南

本指南适用于 `money-app-biz` 的新增代码和架构调整。它服务于阶段 4 的渐进式防回归：**不把历史兼容债务伪装成新设计，也不要求一次性重写历史代码。**

## 1. 先确认代码归属

| 类型 | 应放位置 | 责任 |
| --- | --- | --- |
| HTTP Controller | `feature.<name>.interfaces.rest` | 参数校验、鉴权、调用应用服务、返回 HTTP DTO；不承载查询拼装或持久化。 |
| 应用服务 | `feature.<name>.application` | 用例编排、事务边界、调用本域领域/基础设施能力和明确的跨域契约。 |
| 本域持久化 | `feature.<name>.infrastructure.persistence` | Mapper 和数据库访问实现；只能被本域应用/领域层使用。 |
| 运行时能力 | `platform.runtime` | 工作区、文件、内嵌数据库等平台能力；不得依赖任何 `feature.*`。 |
| 跨域写侧协调 | `feature.trade.application.boundary.facade` | 仅用于收银、退款等既有交易场景的 GMS/UMS 写侧契约。 |

SYS 和尚未迁移的旧包可以保留兼容入口，但新增类型仍应优先归入对应 `feature` 或 `platform` 包。

## 2. 允许与禁止的依赖

### Controller

允许：Controller 调用本 Feature 的应用服务接口或明确的应用服务。

禁止：Controller 直接注入或导入任何 `*Mapper`；Controller 不应直接使用 MyBatis 查询链、拼装跨域数据或调用其他 Feature 的 Mapper/`ServiceImpl`。

当 Controller 需要新查询时，先在本 Feature 的应用服务定义用例方法；Mapper 只能由该服务或本域基础设施层使用。

### Feature 之间

允许：调用对方 Feature 的稳定服务接口；TRADE 的收银、退款写侧继续使用既有 `PosPricingFacade`、`GoodsStockFacade` 和 `MemberAssetFacade` 及其场景 DTO。

禁止：导入其他 Feature 的 `ServiceImpl`、Mapper、基础设施实现或内部辅助类。需要跨域能力时，先新增/复用一个面向场景的服务接口、facade 或 DTO，而不是暴露实现类。

### Entity 和 DTO

新跨域输入/输出必须使用场景 DTO；不要把对方 Feature 的内部 Entity 作为新方法参数、返回值或 Web 接口契约。

当前 `com.money.entity` 仍是共享兼容包。它在阶段 4.2 完成实体归属前不是自动失败条件，但新增跨域 Entity 使用必须在变更说明中写明兼容理由和后续 DTO 替代计划。

### Platform

`platform` 可被应用启动与基础设施配置使用，但 `platform` 不得导入 `feature.*`。平台若需要业务信息，应由调用方传入配置或定义中立能力契约，不能反向依赖业务 Feature。

## 3. 历史基线不是新增许可

扫描基线 v1 记录了七个历史文件；其中 `SysStrategyController` 和 `GmsStockLogController` 已完成迁移。当前尚未迁移的 Controller-Mapper 文件是：`GmsBrandConfigController`、`GmsGoodsExcelController`、`PosCouponRuleController`、`UmsMemberController`、`UmsMemberImportController`。

它们只能在单独的迁移切片中减少，不能作为新代码直接依赖 Mapper 的依据。若不得不保留某项兼容依赖，变更必须说明：原因、影响范围、移除条件和对应清单项。

## 4. 提交前自检

1. 确认新增类位于正确的 Feature 或 `platform` 包。
2. 确认 Controller 没有新增 `*Mapper` 导入。
3. 确认没有新增跨 Feature 的 `ServiceImpl`、Mapper 或内部辅助类导入。
4. 确认跨域方法没有新增内部 Entity 契约；如暂用共享 Entity，记录兼容理由。
5. 确认 `platform` 没有新增 `feature.*` 导入。
6. 运行与改动相称的测试；涉及包移动、Spring Bean、Mapper 扫描或跨域事务时，至少运行相关集成测试，并在切片收口时运行全量 `mvn test` 和 `mvn package -DskipTests`。
7. 在实施台账和阶段清单中记录基线变化、验证结果、残余风险及下一步。

## 5. 审查结论的写法

架构审查应写成可核验的结论，例如：“新增 `feature.gms` 应用服务调用 GMS Mapper，未新增 Controller-Mapper 或跨 Feature 实现依赖；`mvn test` 通过。”

不要只写“已遵循分层”。若发现违规，优先给出最小迁移路径和可运行的验证方式，而不是在无范围评估时扩张为全局重构。
