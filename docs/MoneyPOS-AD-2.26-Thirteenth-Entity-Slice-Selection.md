# AD-2.26 第十三个共享 Entity 物理归属迁移切片选择

## 结论

第十三个物理归属切片选择 **UMS 的 `UmsRechargeOrder`**。后续实施任务编号为
**AD-2.27**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.ums.infrastructure.persistence.entity`，更新遗留
`UmsRechargeOrderMapper`、UMS 的 `UmsMemberRechargeService`、`UmsMemberAssetService` 和
`UmsMemberAssetController` 的导入，并新增本地 Entity 的持久化、充值/红冲和查询接口回归。

这是当前 17 个已登记共享 Entity 中边界最小的真实所有者切片。生产源码对该类型的直接使用只有一个
UMS Mapper、两个同域资产服务和一个同域 Controller；没有其他 Feature、Maven 模块、DTO/VO、Excel、
序列化适配器、Mapper XML、resultMap、YAML 或资源中的 Entity FQCN。Controller 的
`GET /ums/member/recharge/order/{orderNo}` 的确直接返回该 Entity，但它、调用的服务和实体均属同一
UMS 业务模块；迁移只改 Java 包导入，保持路由、响应 JSON 字段、鉴权和异常语义不变，并以接口回归锁定
该兼容面。因此不需要为了物理归属迁移新增跨域 DTO 或兼容副本。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.27 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.UmsRechargeOrder`；显式 `@TableName("ums_recharge_order")`，实现 `Serializable`，`id` 为 `@TableId(IdType.AUTO)`，字段为单号、会员、类型、金额/赠券/实付、状态、备注、租户和创建时间 | 原样移至 UMS 持久化 entity 包；不改注解、序列化标识、字段、Java 类型、主键策略或默认值。 |
| Mapper | 遗留 `com.money.mapper.UmsRechargeOrderMapper extends BaseMapper<UmsRechargeOrder>`，没有自定义方法、注解 SQL 或 XML | Mapper 包、名称和扫描根不动；只更新泛型导入。 |
| 充值与红冲 | `UmsMemberRechargeService` 在 `@Transactional` 充值中创建 `PAID` 凭证并写会员资产/日志；红冲按 `orderNo` 查询，校验后更新为 `VOID` 并追加撤销原因 | 仅更新 Entity 导入和 Lambda 方法引用的类型；不改随机单号、金额/赠券计算、资产增减、日志、红冲风控、状态、写入顺序、异常或事务。 |
| 查询与 HTTP | `UmsMemberAssetService.getRechargeOrderDetail` 按单号查询一条；`UmsMemberAssetController` 在 `GET /ums/member/recharge/order/{orderNo}` 直接返回该类型，保留 `umsMember:list` 鉴权和空单号异常 | Controller 必须同步更新本域导入；不改 URL、HTTP 方法、请求路径变量、响应字段、OpenAPI 描述、鉴权或异常文本。 |
| 跨域/资源/测试 | 全量搜索未发现其他 Feature、API 消费模块、DTO/VO、JSON/Excel、XML、resultMap、参数/结果类型、YAML 或资源 FQCN；当前没有直接命名 `UmsRechargeOrder` 的测试 | 不新增兼容桥或 API 副本；下一任务须新增测试本地导入，不能把“当前无直接测试”误当成无需回归业务行为。 |
| 表 / Flyway | `V1.0.0__init_database.sql` 创建 `ums_recharge_order`：`id` 自增主键、唯一 `uk_order_no`、`idx_member_id`，并保留金额列默认 `0.00`、状态默认 `PAID` 与创建时间默认值 | 不改表、索引、默认值、种子段或 Flyway；不新增迁移。 |

`UmsRechargeOrder` 的 HTTP 暴露是已存在的 UMS 内部 REST 契约，而非跨 Feature Entity 契约。物理移动后
Controller 的方法签名会使用 UMS 本地持久化类型，但 Jackson 看到的字段与路由输出不变；任何将该类重新留在
API 模块、复制一个兼容类，或趁机改为 DTO 的做法都超出 AD-2.27。Mapper 的泛型 CRUD 能力也不授权新建删除
业务：回归应覆盖迁移实际依赖的插入、按主键/单号读回和红冲更新，不以直接删除凭证来扩大财务记录语义。

迁移后所有权登记预计由 **17** 降至 **16**。`UmsRechargeOrder` 当前在四个业务 Java 文件中由共享包单项导入，
但遗留 `UmsRechargeOrderMapper` 不在扫描的 `feature/**` 范围，而三个 Feature 消费者仍各自导入其他共享 Entity；故
共享 Entity import 文件的报告数预计保持 **62**，owner-local uses 预计由 **98** 降至 **95**。这是扫描报告而非验收目标；
跨所有者桥（6）与通配符基线（3）不得扩大，最终以实际扫描为准。

## AD-2.27 必须补充的回归

1. 新增 `UmsRechargeOrderMapperIntegrationTest`，使用隔离 `money_pos_test` 验证 UMS 本地 Entity 的显式
   `ums_recharge_order` 映射、数据库生成的 AUTO ID、唯一单号、会员/类型、三项金额、状态、备注、租户和创建时间
   可插入并读回；再验证将同一凭证更新为 `VOID` 的持久化结果。不要新增物理删除测试或 SQL。
2. 新增或扩展 UMS 充值服务集成回归：对余额充值断言 `PAID` 凭证、会员余额和充值日志；以该单号执行红冲，断言
   凭证状态/备注、余额回退和撤销日志。保留现有单号生成、余额不足与赠券/券额/券张数风控，不在本切片重写它们。
3. 增加 Controller 回归覆盖 `GET /ums/member/recharge/order/{orderNo}`：在既有鉴权/请求上下文下断言按单号返回的
   字段与服务读回一致，并保留空单号和不存在单号的既有异常语义。接口测试不应改变或替换充值、红冲服务级事务测试。
4. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 与 `git diff --check`；确认 API
   模块无旧 Entity，业务 Java/测试/资源无旧 FQCN，登记减少一项且桥/通配符没有扩大。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `UmsRechargeOrder` | UMS；单 Mapper、两项 UMS 资产服务、同域查询 Controller | 已有 REST Entity 输出，但无跨域或资源引用；可用 Mapper、充值/红冲和查询接口三层回归锁定 | **选择**。 |
| `SysPrintConfig` | SYS；Mapper、`IService<Entity>`、配置 Controller 与收据/钱箱打印服务 | 直接影响运行设备和打印能力，需硬件相关兼容验证 | 延后。 |
| `UmsMemberLog`、`UmsMember`、`UmsMemberBrandLevel`、`PosMemberCoupon`、`PosCouponRule` | UMS；资产/会员/券工作流、服务泛型或跨所有者兼容桥 | 消费者、资产语义、公开兼容面或桥更宽 | 延后。 |
| `GmsInventoryDoc`、`GmsStockLog`、`GmsGoods`、`GmsBrand`、`GmsGoodsCategory`、`PosSkuLevelPrice` | GMS；库存命令、分析、商品目录、价格矩阵或 Excel/POS 查询 | 多消费者、算法或查询兼容面更宽 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` | TRADE；订单聚合、结账、退款和订单查询 | 交易主流程与外部订单兼容面显著更宽 | 延后。 |
| `SysBrandConfig`、`SysStrategy` | SYS；品牌配置、策略读取及 GMS/FIN 兼容面 | 仍含配置或跨域策略消费 | 延后。 |

## AD-2.27 实施与验收边界

1. 只移动 `UmsRechargeOrder`，更新 `UmsRechargeOrderMapper`、两个 UMS 资产服务、
   `UmsMemberAssetController` 和新增/调整的 UMS 测试；不得移动 Mapper、Controller、DTO、其他 Entity、扫描根或跨域契约。
2. 保持显式表名、`Serializable`、`IdType.AUTO`、字段 Java 类型、遗留 Mapper 包、充值/红冲事务、单号生成、
   路由、响应字段、鉴权、表/Flyway 和索引不变；不新增物理删除或财务语义改造。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；复核所有权登记、共享 import 报告、通配符与桥基线均没有意外扩大。

## 下一步

AD-2.27 已闭环：`UmsRechargeOrder` 已移至 UMS 持久化实体包，Mapper、两项资产服务和查询 Controller
均已使用本地类型；Mapper 持久化、余额充值/红冲及查询处理器回归均通过。下一最小任务须由债务清单重新盘点后确定。
