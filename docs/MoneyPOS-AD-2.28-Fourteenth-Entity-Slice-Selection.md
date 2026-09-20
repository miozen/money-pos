# AD-2.28 第十四个共享 Entity 物理归属迁移切片选择

## 结论

第十四个物理归属切片选择 **SYS 的 `SysPrintConfig`**。后续实施任务编号为
**AD-2.29**：仅将它从 `money-app-api: com.money.entity` 移至
`money-app-biz: com.money.feature.sys.infrastructure.persistence.entity`，更新遗留
`SysPrintConfigMapper`、`SysPrintConfigService`、`SysPrintConfigServiceImpl`、
`SysPrintConfigController` 和 `PosPrinterService` 的导入，并补固定配置记录的 Mapper 与配置接口回归。

这是当前 16 个已登记共享 Entity 中最小且可验证的真实所有者切片。直接生产使用仅有一个遗留 Mapper、
本域服务接口/实现、SYS 配置 Controller，以及打印服务；没有其他 Feature、Maven 模块、DTO/VO、
JSON 适配器、Excel、Mapper XML、resultMap、YAML 或资源中的 Entity FQCN。Controller 直接接收和返回
该类型、`PosPrinterService` 直接读取该类型，但它们都在同一 SYS/遗留应用边界内；物理迁移只改本地导入，
不改变 `/system/config/print` 的 JSON、路由、固定记录或硬件命令。

## 已确认的消费面与固定约束

| 面 | 当前事实 | AD-2.29 边界 |
| --- | --- | --- |
| Entity | `money-app-api: com.money.entity.SysPrintConfig`；显式 `@TableName("sys_print_config")`，继承 `BaseEntity`，自身以 `@TableId(IdType.INPUT)` 覆盖 ID，含门店信息、头尾文本、自动打印和开钱箱开关 | 原样移至 SYS 持久化 entity 包；不改基类、INPUT 主键、注解、字段或 Java 类型。 |
| Mapper | 遗留 `com.money.mapper.SysPrintConfigMapper extends BaseMapper<SysPrintConfig>`，无自定义 SQL、注解 SQL 或 XML | Mapper 包、名称和扫描根不动；只更新泛型导入。 |
| 服务与 Controller | `SysPrintConfigService extends IService<SysPrintConfig>`；实现永远以 ID=1 查询/更新，Controller 的 `GET /system/config/print` 直接返回、`POST /system/config/print/update` 直接接收该类型 | 同步更新本域服务、实现和 Controller 导入/泛型；不改 ID 强制覆盖、路由、HTTP 方法、响应字段、OpenAPI 文案或失败异常。 |
| 打印运行面 | `PosPrinterService.printReceiptAndOpenDrawer` 以 Mapper `selectById(1L)` 读取配置，再依 `openDrawer` 与 `autoPrint` 组装/下发 ESC/POS 命令 | 只更新局部变量的 Entity 导入；不改读取时机、ID、空配置早退、字节序列、硬件调用、吞没/记录异常或任何打印格式。 |
| 跨域 / 资源 / 测试 | 全量搜索未发现其他 Feature、模块、DTO/VO、序列化、XML、resultMap、参数/结果类型、YAML 或资源 FQCN；当前没有直接命名该 Entity 的测试 | 不新增 API 兼容副本、DTO 或跨域桥；下一任务必须新增本地 Entity 测试导入。 |
| 表 / Flyway | `V1.0.0__init_database.sql` 的 `sys_print_config` 只有固定主键记录 `id=1`，含八个配置字段、`BaseEntity` 审计列及默认 `tenant_id=0`；Flyway 插入默认门店配置 | 不改表、固定主键、种子记录、审计列、默认值或 Flyway；不新增迁移。 |

`SysPrintConfig` 的 Controller 暴露是已有 SYS 管理契约，不是跨 Feature Entity 契约。物理移动后，Controller
签名使用 SYS 本地持久化类型，但 Jackson 字段不变；不得保留 API 副本或趁迁移改为 DTO。`BaseEntity` 与
实体自身的 INPUT ID 共同服务于固定 ID=1 的唯一配置记录，不能用“通用 CRUD”把它改造成可新增、可删除的
配置集合。`PosPrinterService` 的实际出纸/钱箱动作依赖主机硬件，仍是已有的人工环境验收项；本次应以它读取
的同一固定配置记录及静态调用面保护，而非在隔离数据库中伪造打印机设备。

迁移后所有权登记预计由 **16** 降至 **15**。所有直接生产消费者都位于遗留 `com.money` 扫描根、而非
`feature/**`；故共享 Entity import 文件报告预计保持 **62**、owner-local uses 保持 **95**，跨所有者桥（6）与
通配符基线（3）不得扩大。以上都是报告指标，最终以实际扫描为准。

## AD-2.29 必须补充的回归

1. 新增 `SysPrintConfigMapperIntegrationTest`，在隔离 `money_pos_test` 读取 Flyway 的 ID=1 种子记录，验证
   SYS 本地 Entity 的显式表名、INPUT 主键、八项配置字段和继承审计字段可读；在测试事务内更新同一记录并读回。
   不插入第二条记录，也不删除唯一配置。
2. 新增或扩展 SYS 配置服务/Controller 回归：传入非 1 的 ID 更新后，断言服务仍更新 ID=1；通过
   `GET /system/config/print` 断言门店、文本和两个开关字段保持 JSON/Java 契约。保留更新失败时的既有
   `硬件打印配置更新失败，请重试！` 异常路径；不触发真实打印机或钱箱。
3. 使用静态搜索确认 `PosPrinterService` 仍读取本地类型并固定 `selectById(1L)`；不以测试替换人工硬件
   验收，也不改变 ESC/POS 字节、自动打印/开钱箱条件或异常处理。
4. 运行上述专项、隔离 `money_pos_test` 全量测试、打包、架构扫描夹具、`--check-new` 和 `git diff --check`；确认
   API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN，登记减少一项且桥/通配符没有扩大。

## 候选对比与排除理由

| 候选 | 所有者 / 生产面 | 风险 | 决定 |
| --- | --- | --- | --- |
| `SysPrintConfig` | SYS；单 Mapper、本域 `IService`/实现/Controller、打印服务 | HTTP 与硬件读取面均同域，固定 ID=1 与配置读写可由 Mapper/接口回归锁定；硬件动作保持人工验收 | **选择**。 |
| `SysStrategy`、`SysBrandConfig` | SYS；策略/品牌配置服务、Controller，且已有 GMS/FIN 或商品兼容面 | 跨所有者策略/品牌读取或更宽配置兼容面 | 延后。 |
| `UmsMemberLog`、`UmsMember`、`UmsMemberBrandLevel`、`PosMemberCoupon`、`PosCouponRule` | UMS；资产、会员、券工作流、接口或既有跨所有者桥 | 消费者、资产语义、公开契约或桥更宽 | 延后。 |
| `GmsInventoryDoc`、`GmsStockLog`、`GmsGoods`、`GmsBrand`、`GmsGoodsCategory`、`PosSkuLevelPrice` | GMS；库存命令、分析、目录、价格矩阵、Excel/POS 查询 | 多消费者、算法或查询兼容面更宽 | 延后。 |
| `OmsOrder`、`OmsOrderDetail` | TRADE；订单聚合、结账、退款和订单查询 | 交易主流程与订单兼容面显著更宽 | 延后。 |

## AD-2.29 实施与验收边界

1. 只移动 `SysPrintConfig`，更新 Mapper、服务接口/实现、Controller、`PosPrinterService` 和新增/调整测试；不得移动
   Mapper、服务、Controller、DTO、其他 Entity、扫描根或跨域契约。
2. 保持显式 `sys_print_config`、`BaseEntity`、INPUT ID、唯一 ID=1 语义、审计填充、种子记录、表/Flyway、
   路由/JSON/异常、打印读取时机和 ESC/POS 行为不变；不新增删除/多配置语义或硬件模拟实现。
3. 使用 `rg` 确认 API 模块无旧 Entity，业务 Java/测试/资源无旧 FQCN；复核所有权登记、共享 import 报告、
   通配符与桥基线均没有意外扩大。

## 下一步

AD-2.29 已闭环：`SysPrintConfig` 已移至 SYS 持久化实体包，Mapper、服务接口/实现、Controller 和打印服务
均已使用本地类型；固定 ID=1 的 Mapper 读写及配置接口回归均通过。真实打印机/钱箱动作仍为既有人工环境验收项，
下一最小任务须由债务清单重新盘点后确定。
