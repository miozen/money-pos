# MoneyPOS 阶段 2 业务包边界整理执行清单

## 使用规则

- 阶段 2 的完成不是“迁移若干服务”，而是 GMS、UMS、TRADE、FIN、HOME 五个目标 Feature 的主要生产类型具有可识别包归属。
- 每个清单项只能涉及一个 Feature；完成前必须做调用面扫描、编译、相关回归，并更新实施台账。
- 每个已完成的 Feature 切片必须独立提交；禁止把后续 Feature 的改动带入该提交。
- URL、Controller 方法签名、前端 API 路径、表名、Flyway、DTO/Entity 兼容包均不得改变。
- Mapper 的迁移必须先确认 `@MapperScan` 或等价扫描范围仍能发现其新包；不能以编译通过替代 Spring 上下文验证。

## 阶段完成门槛

- [ ] GMS、UMS、TRADE、FIN、HOME 的主要 Controller、Service、ServiceImpl、Mapper 已按归属放入逻辑 Feature 包，或在台账中逐项记录为什么属于共享兼容层。
- [ ] TRADE 的写侧不再直接调用 GMS/UMS 内部 Mapper 或实现类；Facade 仍为跨域写侧入口。
- [ ] 所有保留在旧包的 DTO/Entity 都有兼容理由；没有全量 DTO/Entity 搬迁。
- [ ] Spring Bean 无重名，Mapper 扫描正常；`mvn test`、`mvn package`、阶段 0 套件通过。
- [ ] 收银、退款、订单、财务与首页完成手工冒烟回归。
- [ ] 每个 Feature 切片均可用独立 Git 提交回滚。

## 2.0 现有基线与提交纪律

- [x] 2.0.1 已审核当前未提交的阶段 0、1、2 变更；范围与本轮测试、Facade、TRADE/FIN 迁移及台账一致，未发现额外业务文件。
- [x] 2.0.2 已运行 `test-compile` 与 `CheckoutIntegrationTest`：10 tests, 0 failures, 0 errors。
- [x] 2.0.3 已将历史已验证工作整理为补救性基线提交；该提交不追溯宣称为“每 Feature 一次提交”。
- [x] 2.0.4 后续切片开始严格执行独立提交，并在实施台账记录提交号与回滚范围。

## 2.1 FIN Feature 完整切片

当前已迁移：销售分析、指标装配、利润分析、风险分析。

- [x] 2.1.1 已盘点 3 个服务对、`FinanceDashboardAssembler`、2 个 Controller 和 1 个 FIN 专属 Mapper；Controller 是服务的唯一直接调用方。
- [x] 2.1.2 已盘点两个 Controller 与 `FinanceReportMapper`；扫描配置已由单一旧包扩展为旧共享 Mapper 包与 FIN Mapper 包。
- [x] 2.1.3 已迁移剩余 FIN 服务对及专属装配器。
- [x] 2.1.4 已迁移两个 FIN Controller 与 `FinanceReportMapper`；共享 OMS/GMS/UMS Mapper 保留兼容层。
- [x] 2.1.5 已完成 FIN 服务执行性集成测试、编译、Spring 上下文与阶段 0 回归；FIN Feature 以独立提交收口。带日期参数的瀑布流 SQL 与租户拦截器冲突记录为既有残余风险。

## 2.2 HOME Feature 完整切片

- [x] 2.2.1 为 `GET /home/count` 补快照特征测试：首次创建、同日更新、输出结构。`HomeCountSnapshotCharacterizationTest` 验证首次调用创建当天快照、同日再次调用保持同一快照 ID 并更新内容，以及 `today`、`month`、`year`、`total`、`inventoryValue`、`alerts` 六个顶层字段。
- [x] 2.2.2 已盘点 `HomeController`、`HomeService`、`DecisionEngineService` 的所有跨域读取与写入：`/home/count` 是决策引擎唯一生产调用方，`/home/charts` 是图表服务唯一生产调用方；HOME 读取 GMS 服务接口及 OMS/UMS Mapper，决策引擎保留通过 Mapper/JDBC 的快照写入。`oms_daily_summary` 有 `UNIQUE(record_date)`，但现有先查再插路径没有应用层同步。
- [x] 2.2.3 已在不改变现有写时机、SQL、唯一键处理或事务行为的前提下，迁移 HOME 控制器、服务和决策引擎到 `feature.home`：接口与实现位于 `feature.home.application`，控制器位于 `feature.home.interfaces.rest`。
- [ ] 2.2.4 已完成 Spring 上下文、快照写入和阶段 0 自动回归，并以当前 HOME-only 提交收口。Windows/Electron 首页手工冒烟因当前源码运行于 WSL 而延期至可启动桌面环境时执行；不阻塞后续阶段 2 代码调整，但仍是阶段 2 最终验收项。

## 2.3 TRADE Feature 完整切片

当前已迁移：checkout、refund、订单查询、订单明细/日志、Facade 与交易支持服务。

- [x] 2.3.1 已盘点 `PosService`、剩余 Pos/OMS Controller、Pos/OMS Mapper 的调用面与扫描范围：`PosController`、`OmsOrderController` 与 `PosService` 归属 TRADE；`PosGoodsController`、`PosCouponRuleController`、`OmsSalesAnalysisController` 分别保留给 GMS、UMS、FIN。`OmsOrderLogMapper` 与 `OmsRefundIdempotentMapper` 仅被 TRADE 使用，可随 TRADE 迁移；订单、支付、分析、会员券与等级价格 Mapper 被 HOME/FIN/GMS/UMS 复用，暂留共享兼容包。Mapper 扫描需保留 `com.money.mapper` 并加入 TRADE 新包。
- [x] 2.3.2 已迁移明确归属的 TRADE 类型：`PosController`、`OmsOrderController`、`PosService`、其实现，以及仅被 TRADE 使用的订单日志和退款幂等 Mapper；共享 Mapper 的兼容理由已记录在实施台账。
- [x] 2.3.3 静态扫描确认迁移后的 TRADE 写侧未引入 GMS/UMS Mapper 或 `ServiceImpl` 依赖；结算写入仍经既有 checkout 与 Facade 边界。
- [ ] 2.3.4 完成结算、退款、订单查询、POS 接口回归，单独提交。

## 2.4 GMS Feature 完整切片

当前已完成周转预警、库存分析、库存单据与入出库单切片；商品目录及价格/库存的共享依赖仍待逐项盘点。

- [x] 2.4.1 已按商品目录、价格库存、库存单据/分析盘点 GMS 调用图。商品目录包含品牌、分类、商品、组合、导入和价格；价格库存包含商品库存计算、库存日志和 POS 等级价；库存单据/分析包含入出库单、库存单据、周转与库存分析。TRADE 继续经 Facade 使用商品与库存写侧；被 FIN、HOME、UMS 或 TRADE 复用的 Mapper 保留兼容包。
- [x] 2.4.2 已先迁移不被 TRADE 直接注入的 GMS 周转预警子域；TRADE 写侧仍通过 Facade。
- [x] 2.4.3 已完成周转预警子域的 Controller、Service、实现和专属 Mapper 迁移，并通过阶段 0 回归；其余 GMS 子域继续逐个迁移、验证与提交。
- [x] 2.4.4 已将库存单据与入出库单作为一个切片迁移：控制器和两个服务对归入 `feature.gms`，入出库单专属 Mapper 归入 GMS 持久化包；被 TRADE/FIN/GMS 复用的商品、库存日志和库存单据 Mapper 保留兼容包。编译和阶段 0 回归通过。
- [x] 2.4.5 已完成商品目录与价格/库存调用面盘点，并识别品牌与分类元数据为首个安全目录切片。商品主档、套餐、价格、库存与库存日志类型因仍被 TRADE、HOME、UMS 或共享 Mapper 调用，暂留兼容包。
- [x] 2.4.6 已迁移品牌与分类元数据：两个 Controller、两个服务对及仅由 GMS 使用的分类 Mapper 已归入 `feature.gms`；品牌 Mapper 因 UMS 导入和 GMS Excel 调用继续保留兼容包。编译与阶段 0 回归通过。
- [x] 2.4.7 已完成商品主档服务组兼容边界设计：`GmsGoodsService` 保持为 TRADE/HOME/GMS 的临时公开兼容接口；商品、套餐、价格、库存、日志 Mapper 保留共享包，库存日志服务因仍是 TRADE 写入 API 而不纳入商品主档切片。
- [x] 2.4.8 已迁移商品主档、套餐/价格/库存助手及商品 Excel 入口到 `feature.gms`，保留 `IService` 兼容 API、路由、事务、SQL 与共享 Mapper；HOME、TRADE 和 GMS 内部调用已更新，编译与阶段 0 回归通过。
- [ ] 2.4.9 运行商品、库存、POS 结算与退款回归。

## 2.5 UMS Feature 完整切片

当前已完成 UMS 调用面盘点及会员流水查询切片；会员档案、资产、充值、导入与 POS 兼容入口仍待逐子域整理。

- [x] 2.5.1 已按会员档案、资产/券、充值与导入盘点 UMS 调用图；`UmsMemberService` 是 TRADE/FIN 共享查询入口，会员等级、会员、流水 Mapper 被多个 Feature 使用。
- [x] 2.5.2 已确认 `MemberAssetFacade` 仍是 TRADE 结算与退款的写侧入口；`UmsMemberAssetService` 未被 TRADE 直接注入。
- [x] 2.5.3 已完成会员流水、会员资产/充值、会员档案/导入三个 UMS 子域迁移；`UmsMemberPosController` 已归入 TRADE 展示层。UMS Mapper 均有明确共享兼容理由：会员流水与等级 Mapper 被 FIN/HOME/TRADE 使用，会员与充值单 Mapper 被多个 UMS 子域使用。
- [ ] 2.5.4 运行余额、优惠券、混合支付、退款资产恢复回归。

## 2.6 阶段 2 收口验证

- [ ] 2.6.1 运行静态依赖扫描、Spring Bean/Mapper 扫描检查。
- [ ] 2.6.2 运行 `mvn test`、`mvn package` 和阶段 0 集成测试。
- [ ] 2.6.3 执行 POS 收银、全单/部分退款、订单查询、财务报表、首页看板手工回归。
- [ ] 2.6.4 更新实施台账，明确进入阶段 3 的前置条件已经满足。
