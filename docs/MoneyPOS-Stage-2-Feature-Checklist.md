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

- [ ] 2.2.1 为 `GET /home/count` 补快照特征测试：首次创建、同日更新、输出结构。
- [ ] 2.2.2 盘点 `HomeController`、`HomeService`、`DecisionEngineService` 的所有跨域读取与写入。
- [ ] 2.2.3 在不改变现有写时机、SQL、唯一键处理或事务行为的前提下，迁移 HOME 控制器、服务和决策引擎到 `feature.home`。
- [ ] 2.2.4 验证首页路由、快照写入、Spring Bean 与阶段 0 回归，并单独提交。

## 2.3 TRADE Feature 完整切片

当前已迁移：checkout、refund、订单查询、订单明细/日志、Facade 与交易支持服务。

- [ ] 2.3.1 盘点 `PosService`、剩余 Pos/OMS Controller、Pos/OMS Mapper 的调用面与扫描范围。
- [ ] 2.3.2 迁移 TRADE 的控制器和明确归属的持久化类型；共享/跨 Feature 查询 Mapper 必须记录兼容理由。
- [ ] 2.3.3 静态扫描确认 TRADE 写侧没有重新引入 GMS/UMS Mapper 或 `ServiceImpl` 依赖。
- [ ] 2.3.4 完成结算、退款、订单查询、POS 接口回归，单独提交。

## 2.4 GMS Feature 完整切片

当前未开始：9 个 Controller、11 个服务接口、8 个实现、12 个 Mapper 仍在旧包。

- [ ] 2.4.1 以商品目录、价格库存、库存单据/分析三个子域盘点 GMS 调用图。
- [ ] 2.4.2 先迁移不被 TRADE 直接注入的 GMS 内部类型；TRADE 写侧继续通过 Facade。
- [ ] 2.4.3 迁移 GMS Controller、Service、实现和明确归属的 Mapper，逐子域验证并单独提交。
- [ ] 2.4.4 运行商品、库存、POS 结算与退款回归。

## 2.5 UMS Feature 完整切片

当前未开始：5 个 Controller、6 个服务接口、2 个实现、4 个 Mapper 仍在旧包。

- [ ] 2.5.1 以会员档案、会员资产、券与充值三个子域盘点 UMS 调用图。
- [ ] 2.5.2 保持 `MemberAssetFacade` 为 TRADE 的写侧边界；不允许 TRADE 重新进入 UMS 内部实现。
- [ ] 2.5.3 迁移 UMS Controller、Service、实现和明确归属的 Mapper，逐子域验证并单独提交。
- [ ] 2.5.4 运行余额、优惠券、混合支付、退款资产恢复回归。

## 2.6 阶段 2 收口验证

- [ ] 2.6.1 运行静态依赖扫描、Spring Bean/Mapper 扫描检查。
- [ ] 2.6.2 运行 `mvn test`、`mvn package` 和阶段 0 集成测试。
- [ ] 2.6.3 执行 POS 收银、全单/部分退款、订单查询、财务报表、首页看板手工回归。
- [ ] 2.6.4 更新实施台账，明确进入阶段 3 的前置条件已经满足。
