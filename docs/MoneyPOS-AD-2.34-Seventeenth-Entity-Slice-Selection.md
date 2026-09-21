# AD-2.34 第十七个共享 Entity 物理归属迁移切片选择

## 结论

第十七个物理归属切片选择 **UMS 的 `UmsMemberBrandLevel`**。后续实施任务编号为
**AD-2.35**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.ums.infrastructure.persistence.entity`，更新遗留
`UmsMemberBrandLevelMapper`、五个 UMS 会员服务消费者和受影响测试导入，并增加会员品牌等级矩阵 Mapper
持久化回归。

这是余下 13 个已登记共享 Entity 中边界最小的真实 UMS 所有者切片。它是会员在各品牌下的等级矩阵：行由
会员 ID、品牌、等级代码、租户和审计时间构成，所有写入和读取均在 UMS 内完成。TRADE 结账只使用 UMS 提供的
`CheckoutPricingBenefitSnapshot` 与 `MemberPosProfileSnapshot` 等 Entity-free 契约；HOME 的会员分布只使用
Mapper 返回的 `List<Map<String, Object>>` 聚合结果，不取得实体。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.35 边界 |
| --- | --- | --- |
| Entity | API 包的 `UmsMemberBrandLevel` 显式映射 `ums_member_brand_level`，以 `IdType.AUTO` 主键，字段为 `id`、`memberId`、`brand`、`levelCode`、`tenantId`、`createTime`、`updateTime` | 原样迁至 UMS 持久化 entity 包；不得改变表名、AUTO ID、字段、Java 类型、Swagger 注解或审计时间语义。 |
| 表 | Flyway 表有 AUTO_INCREMENT `id`、`uk_member_brand(member_id, brand)`、`tenant_id` 默认 0、创建/更新时间默认/更新规则 | 保持唯一键、默认值、审计列、Flyway 与已有数据；不得加入软删除列、改索引、改时间填充或调整租户模型。 |
| Mapper | 遗留 `UmsMemberBrandLevelMapper extends BaseMapper<UmsMemberBrandLevel>`，另有 `getHomeMemberDistributionData()` 注解 SQL，以 INNER JOIN `ums_member` 并过滤 `m.deleted = 0` | Mapper 包、名称、扫描根和聚合 SQL 原样保留；仅更新泛型导入。 |
| UMS 写侧 | `UmsMemberProfileService.saveBrandLevels` 与 `UmsMemberImportService.saveBrandLevels` 均按会员先删后写等级矩阵；导入路径先合并旧矩阵和 Excel 值 | 不改删除/插入顺序、空等级跳过、导入合并、唯一键约束、事务或会员导入语义。 |
| UMS 读侧 | `UmsMemberProfileService` 组装会员列表/详情品牌等级，`UmsMemberAssetExcelExportService` 导出等级矩阵，`MemberPosProfileQueryService` 与 `CheckoutPricingBenefitQueryService` 映射为稳定的快照/Map | 只更新本域类型导入与 Lambda/泛型引用；保持列表/详情字段、Excel、结账优惠和 POS 画像快照的品牌-等级映射、空值和排序。 |
| HOME / TRADE | HOME 分布 Mapper 方法返回 Map 聚合，TRADE 不导入 Entity 或 Mapper；TRADE 集成测试仅写入本地测试夹具 | 不新增跨域 Entity import、桥或兼容副本；测试夹具同步使用 UMS 本地类型。 |
| 资源 / HTTP | 未发现 Controller、API DTO、XML/resultMap、YAML、其他 Maven 模块或资源 FQCN 消费实体 | 不改路由、DTO、响应字段、资源、Mapper 扫描根或契约。 |

生产直接实体消费者是遗留 Mapper 与五个 UMS 服务：`UmsMemberProfileService`、`UmsMemberImportService`、
`UmsMemberAssetExcelExportService`、`MemberPosProfileQueryService` 和 `CheckoutPricingBenefitQueryService`。
没有生产跨 Feature Entity 使用；`HomeDailyMemberQueryService` 仅调用 Mapper 的聚合 Map 方法。

## AD-2.35 必须补充的回归

1. 新增 `UmsMemberBrandLevelMapperIntegrationTest`，在隔离 `money_pos_test` 使用 UMS 本地 Entity 插入、读回、
   更新和删除唯一会员/品牌夹具，断言 AUTO ID、所有字段、审计时间、租户及唯一矩阵字段；不改 SQL、表或索引。
2. 保留并更新 `UmsMemberImportServiceIntegrationTest`：导入合并后的品牌/等级矩阵及既有会员导入语义不变。
3. 保留并更新 `HomeMemberDistributionQueryIntegrationTest`：Mapper 的 INNER JOIN、已删除会员过滤和品牌-等级聚合口径不变。
4. 保留并更新 `UmsMemberAssetExcelExportServiceIntegrationTest`、`CheckoutPricingBenefitQueryServiceIntegrationTest` 和
   `UmsMemberPosControllerIntegrationTest`：Excel、结账优惠和 TRADE 获取的会员 POS 快照保持 Entity-free。
5. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 与 `git diff --check`；确认 API、
   业务 Java/测试/资源无旧 FQCN，登记减少一项且桥/通配符不扩大。

迁移后所有权登记预计由 **13** 降至 **12**。Feature 共享 Entity import 文件预计由 **59** 降至 **55**：
`UmsMemberProfileService` 和 `UmsMemberImportService` 仍导入其他共享 Entity，三个其余 UMS 消费文件不再导入
共享 Entity。owner-local uses 预计由 **84** 降至 **79**；跨所有者桥（6）与通配符基线（3）必须不变。均为报告指标，
最终以扫描为准。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `UmsMemberBrandLevel` | UMS；一个 Mapper、五个 UMS 读写/快照/Excel 消费者，无生产跨域 Entity 使用 | 需锁定导入、等级矩阵和快照口径，但均已有局部回归 | **选择**。 |
| `UmsMemberLog`、`UmsMember`、`PosMemberCoupon`、`PosCouponRule` | UMS；资产、主档、券、结算、退款、Controller 或 TRADE 兼容桥 | 事务、公开接口或跨所有者兼容面较宽 | 延后。 |
| `GmsStockLog`、`GmsBrand`、`GmsGoodsCategory`、`GmsGoods` | GMS；库存、目录、商品 HTTP/Excel/算法面 | Controller 或核心库存/商品算法面较宽 | 延后。 |
| `SysBrandConfig`、`SysStrategy` | SYS；仍被 GMS 直接读取 | 已登记 GMS→SYS Entity 桥，须先收敛为窄契约 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` | TRADE；结账、退款、查询、报表与打印 | 核心交易和兼容面最宽 | 延后。 |

## AD-2.35 实施与验收边界

1. 只移动 `UmsMemberBrandLevel`，更新 Mapper、五个 UMS 消费者和受影响测试；不得移动 Mapper、Controller、DTO、
   其他 Entity、扫描根或跨域契约。
2. 保持显式表名、AUTO ID、字段、唯一键、审计时间、租户、聚合 SQL、导入/保存顺序、Excel、结账/POS/HOME 口径、
   路由、表/Flyway 不变；不得新增跨域 Entity import 或 DDL。
3. 使用 `rg` 确认无旧 FQCN，并复核所有权登记、共享 import、通配符和桥基线没有意外扩大。

## 下一步

唯一下一最小任务为 **AD-2.35：迁移 `UmsMemberBrandLevel` 到 UMS 持久化实体包，并补 Mapper、会员导入、HOME 分布、
Excel、结账与 POS 快照回归。**
