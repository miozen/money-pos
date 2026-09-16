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
- [x] 4.2 已发布 `MoneyPOS-Entity-Ownership-and-DTO-Plan.md`：为 37 个共享 Entity 建立 GMS、UMS、TRADE、HOME、SYS 的逻辑归属，并按 P0–P3 排定跨域 DTO 切片。P0 是优惠券规则接口直接暴露 `PosCouponRule`；P1 是 TRADE 对商品/会员 `IService<Entity>` 的跨域读取；P2 是 FIN/HOME 读模型。未移动实体、未改变表或契约；下一项是 4.3 的非阻断扫描报告。
- [x] 4.3 已新增 `scripts/architecture-scan.sh` 与 `MoneyPOS-Architecture-Scan-Baseline-v1.md`：脚本输出 Controller-Mapper、跨 Feature `ServiceImpl`/Mapper、`platform → feature` 和共享 Entity 的可重复 Markdown 报告，始终非阻断；v1 固定 7/0/0/62 的基线与例外。连续两个切片稳定且 P0 完成后，才评估“仅阻止新增项”的门禁；下一项是 4.4 首个 Controller-Mapper 迁移切片。
- [x] 4.4 已迁移低耦合的 `SysStrategyController`：新增 `SysStrategyService`/`SysStrategyServiceImpl` 承担原有唯一全局策略的读取、空对象兜底与按既有 ID 更新/首次插入语义，Controller 不再导入 Mapper；路由 `/sys/strategy/get`、`/sys/strategy/save`、请求/返回类型均未改变。新增隔离数据库集成测试通过，架构扫描由 v1 的 7 项降为当前 6 项；下一项是 4.5 的全量收口验证。
- [x] 4.5 已完成两个迁移切片的全量收口：第二轮隔离 `money_pos_test` 上的 10 个测试类、24 项用例均通过（0 failures / 0 errors），`mvn package -DskipTests` 成功，扫描由初始 7 降为 5 个 Controller-Mapper，跨 Feature 实现/Mapper 与 `platform → feature` 均为 0。共享 Entity 导入为 64，其中新增的 2 项是 GMS 本域查询服务的合法使用；`MoneyPOS-Architecture-Scan-Report-Stage4.5.md` 已记录。
- [x] 4.5.1 P0 优惠券规则 DTO 已收口：`PosService.getValidCouponRules()` 与 `/ums/member/coupon-rules` 已改为返回 `CouponRuleSummary`，只保留 `id`、`name`、`thresholdAmount`、`discountAmount`、`status`，不再暴露 `PosCouponRule` 持久化字段；新增端点集成测试。隔离 `money_pos_test` 的全量测试、打包和扫描通过，当前扫描为 5/0/0/63（Controller-Mapper / 跨 Feature 实现或 Mapper / platform → feature / 共享 Entity 导入）。两个稳定迁移样本及 P0 已齐备；下一最小任务是实现前三项扫描的 additions-only（仅新增违规）比较门禁，Entity 规则继续保持非阻断直至 P1。
- [x] 4.5.2 已实现并验证本地 additions-only 门禁：`bash scripts/architecture-scan.sh --check-new` 将 v1 的 7 个历史 Controller-Mapper 文件作为允许清单，跨 Feature `ServiceImpl`/Mapper 与 `platform → feature` 基线为零；新增发现才以退出码 1 失败。当前源码报告与门禁均通过（5/0/0/63），未知参数按预期以退出码 2 拒绝。默认无参数运行仍为报告模式，门禁尚未接入 Maven 或 CI；共享 Entity 继续仅统计、不阻断。下一最小任务是进行一次独立开发流程复核，再决定是否接入 Maven 或 CI。
- [x] 4.6.1 已迁移 `PosCouponRuleController`：新增 `feature.trade.application.coupon.CouponRuleManagementService`，承接原有分页筛选、规则增改删和会员未使用券汇总；路由 `/pos/couponRule`、请求/返回类型、排序和聚合字段均未改变，Controller 不再导入任一 Mapper。新增集成测试覆盖上述行为；全量测试、打包和 additions-only 门禁通过。扫描降为 4/0/0/65；新增的 2 个共享 Entity 导入位于 TRADE 本域服务内部，仍只跟踪。下一最小任务是盘点剩余 4 个 Controller-Mapper 项，选择低耦合的下一切片。
- [x] 4.6.2 已完成剩余 4 项盘点：`UmsMemberController` 的三条排行榜路由是唯一的同域单 Mapper、只读、无 Excel/跨域参考数据候选，选为下一切片；`GmsBrandConfigController` 需先界定 GMS 对 SYS 配置的窄契约，商品 Excel 与会员导入分别涉及多 Mapper、动态表头、价格/资产批处理，暂不机械迁移。详见 `MoneyPOS-Stage4-Remaining-Controller-Inventory.md`。下一最小任务是迁移 UMS 排行榜查询至 `UmsMemberService`。
- [x] 4.6.3 已迁移 UMS 三条排行榜查询：累计消费、余额、频次 Top 50 已改由 `UmsMemberService` 返回既有 `MemberRankVO`，`UmsMemberController` 不再导入 `UmsMemberMapper`；三个路由与权限标记保持不变。新增隔离数据库集成测试验证三类字段；全量测试、打包与 additions-only 门禁通过。扫描降为 3/0/0/65。下一最小任务是为 `GmsBrandConfigController` 设计 GMS→SYS 品牌配置的窄服务契约。
- [x] 4.6.4a 已完成 GMS→SYS 品牌配置窄契约设计：明确由 SYS 服务独占 `SysBrandConfigMapper`，GMS 应用服务负责 `/gms/brand/config` 的默认值与 JSON 兼容映射；保留“无配置为 `true/null`、空等级为数组、按品牌更新指定字段”的语义。商品 Excel 与 POS facade 的既有 SYS Mapper 使用不混入本切片。详见 `MoneyPOS-Stage4-Gms-Sys-Brand-Config-Contract.md`。下一最小任务是按该设计迁移控制器并补集成测试。
- [x] 4.6.4b 已迁移 `GmsBrandConfigController`：新增中立 `BrandPricingPolicy`/`View` DTO、SYS 配置持久化服务与 GMS 路由应用服务；Controller 不再导入 SYS Entity 或 Mapper。`GET`/`POST /gms/brand/config`、无配置的 `true/null` 默认值、空等级数组、按品牌更新/首次插入语义和前端 JSON 字段保持不变。新增集成测试；全量测试、打包与 additions-only 门禁通过。扫描降为 2/0/0/64。下一最小任务是为 GMS 商品 Excel 入口盘点并拆分动态模板、导出与导入的服务边界。
- [x] 4.6.5a 已完成 GMS 商品 Excel 盘点：模板和全量导出共享动态会员价格表头，适合作为同一个只读 Excel 生成服务迁移；现有导入已由事务型 `GmsGoodsExcelManager` 承担，保持不动。分类、品牌和会员等级读取可经既有 GMS/SYS 服务完成，Controller 无需保留 Mapper。详见 `MoneyPOS-Stage4-Gms-Goods-Excel-Inventory.md`。下一最小任务是实现该只读模板/导出服务、迁移两个 GET 路由并补工作簿集成测试。
- [x] 4.6.5b 已迁移 GMS 商品 Excel 的只读输出边界：新增 `GmsGoodsExcelReadService`，统一生成模板与全量导出工作簿；`GmsGoodsExcelController` 的两个 GET 路由仅委托该服务，导入 POST 仍原样委托事务型 `GmsGoodsExcelManager`。动态会员价表头、下拉、示例行、文件名、商品状态/满减/分类品牌/价格矩阵映射均保持不变。新增工作簿集成测试覆盖模板/导出共同表头、导出数据和空商品导出不查询价格矩阵；隔离 `money_pos_test` 的 15 个测试类、30 项用例通过，打包和 additions-only 门禁通过。扫描降为 1/0/0/64，仅剩 `UmsMemberImportController`。下一最小任务是盘点 UMS 会员导入的模板、导出、导入与批量发券边界，拆出安全子切片。
- [x] 4.6.6a 已完成 UMS 会员导入入口盘点：导入和批量发券已由 UMS 事务服务承担，保持不动；模板是仅依赖 GMS 品牌与 SYS 字典的安全首切片；导出还涉及 UMS 品牌等级和 TRADE 未使用券计数，须在模板稳定后以窄查询契约处理。详见 `MoneyPOS-Stage4-Ums-Member-Import-Inventory.md`。下一最小任务是迁移只读会员 Excel 模板服务并补工作簿测试。
- [x] 4.6.6b 已迁移只读会员 Excel 模板服务：新增 `UmsMemberExcelTemplateService`，模板 GET 路由只作委托，导出也复用其动态表头与等级 code→中文名映射；导入和批量发券未改变。服务通过 GMS 的品牌选择 DTO 与 SYS 的有序字典映射读取数据，不新增跨域 Entity 契约。新增工作簿集成测试验证动态品牌列、会员等级下拉、示例行、工作表和响应头；隔离 `money_pos_test` 的 16 个测试类、31 项用例通过，打包及 additions-only 门禁通过。扫描仍为 1/0/0/64，余下的 Mapper 只服务于会员资产导出。下一最小任务是设计 TRADE 提供未使用满减券聚合的窄查询契约，再迁移会员资产导出。

## 4.0 验收结论

基线可作为后续变更的比较对象：新出现的 Controller-Mapper、跨 Feature `ServiceImpl` / Mapper 和 `platform → feature` 依赖均应被识别；共享 Entity 的跨域规则暂处于建模阶段。阶段 4 的下一最小任务是 **4.1 编写架构协作指南**，不应先直接改动这 7 个 Controller。
