# AD-2.1 共享 Entity 消费者再盘点与首切片选择

## 结论

当前 `money-app-biz` 的 Feature 源码中有 **75 个文件**直接导入共享
`com.money.entity`。这个数是审计起点，不是违规数：所有者在自己的应用、领域和持久化层使用 ORM
Entity 是合法的；本次没有把它机械地升级成失败门禁。

首个物理归属切片 **TRADE 的 `OmsRefundIdempotent` 已由 AD-2.2 完成迁移**。它现在位于
TRADE 持久化 entity 包，仅由退款幂等防线和 TRADE Mapper 实际使用；HTTP、API DTO、跨 Feature
查询契约、Mapper XML 和序列化边界均未改变。

## 审计方法与分类规则

基线命令：

```bash
rg -l '^import com\.money\.entity\.' money-pos/qk-money-app/money-app-biz/src/main/java/com/money/feature | wc -l
```

结果为 75。随后对每个 Entity 以实际符号引用复核，而不是只按类名前缀或通配符 import 判断：

| 分类 | 含义 | 本轮处理 |
| --- | --- | --- |
| 本域持久化合法 | 逻辑所有者的 Service、领域服务、Mapper 或本域查询实现使用 Entity。 | 保留；不作为跨域债务。 |
| 跨域泄露 | 非所有者 Feature 直接把 Entity 用作查询、写入或对外契约。 | 保持报告并记录替换方向；未获授权时不顺带迁移。 |
| 兼容桥 | 遗留 `com.money.service` / `com.money.mapper` 或暂存公开 `IService<Entity>` 的使用。 | 保持兼容，须在具体切片中确认 Mapper、事务、序列化影响。 |

`import com.money.entity.*` 只记作原始扫描命中，必须结合实际类型使用复核；例如 TRADE 订单查询和
退款编排的通配符 import 不能被误解为对全部 GMS/UMS/SYS Entity 的实际跨域使用。

## 当前消费者归属矩阵

下表覆盖当前业务模块实际被导入的全部 29 个共享 Entity。`本域`包括相应 Feature 的应用/领域/
持久化实现；`桥/泄露`只列需要后续单独处理的实际消费面。

| Entity | 逻辑所有者 | 当前消费者分类 | 桥/泄露与后续方向 |
| --- | --- | --- | --- |
| `GmsBrand` | GMS | 本域目录、商品 Excel、品牌名称/选择查询、Mapper | TRADE 历史订单展示不应再以品牌 Entity 取名；已使用品牌名称契约，保留遗留 import 清理。 |
| `GmsGoods` | GMS | 本域商品、库存、价格、POS 快照、Mapper | 无已确认跨域 Entity 契约；TRADE 结账/POS 已改用商品快照。 |
| `GmsGoodsCategory` | GMS | 本域目录、商品查询/Excel、Mapper | 报表名称使用分类名称契约；无首切片候选。 |
| `GmsGoodsCombo` | GMS | 本域商品和库存命令、Mapper | TRADE 仅通过库存命令处理套餐。 |
| `GmsInventoryDoc` | GMS | 本域库存单据、库存命令、FIN 瀑布查询实现 | FIN 使用 API 快照；Mapper 仍是兼容桥。 |
| `GmsInventoryDocItem` | GMS | 本域单据/库存命令、Mapper | 无跨域 Entity 契约。 |
| `GmsInventoryOrder` | GMS | 本域库存订单、Mapper | 无跨域 Entity 契约。 |
| `GmsInventoryOrderDetail` | GMS | 本域库存订单、Mapper | 无跨域 Entity 契约。 |
| `GmsMemberTransaction` | UMS | 仅遗留共享 Mapper，未发现 Feature 应用层实际消费者 | 候选很小但没有行为回归覆盖，暂不作为首切片。 |
| `GmsStockLog` | GMS | 本域库存、分析、查询服务和 Mapper | TRADE 库存写入已收敛为命令；遗留批量日志服务仍为兼容桥。 |
| `GmsTurnoverWarningSnapshot` | GMS | 本域周转服务和专用 Mapper | 单一所有者，但写入时机/告警行为尚无专门回归；暂缓。 |
| `PosSkuLevelPrice` | GMS | 本域商品价格/Excel/POS 快照和 Mapper | 名称虽含 POS，归属仍为 GMS；无跨域 Entity 契约。 |
| `UmsMember` | UMS | 本域档案、资产、导入、查询和 Mapper | TRADE/FIN/HOME 已改用场景快照；遗留接口继承 `IService` 是兼容面。 |
| `UmsMemberBrandLevel` | UMS | 本域会员权益、导入/导出和 Mapper | 会员/品牌展示已走快照；无首切片候选。 |
| `UmsMemberLog` | UMS | 本域资产、充值、日志服务和 Mapper | FIN 已走成员资产快照；遗留 Mapper 是兼容桥。 |
| `UmsRechargeOrder` | UMS | 本域充值/资产和 Mapper | 控制器返回 Entity 的兼容 API 仍需独立 DTO 任务。 |
| `PosCouponRule` | UMS | UMS 权益查询、Mapper | TRADE 优惠券管理仍直接使用它，是已知跨域持久化兼容面；不与 AD-2.2 混合。 |
| `PosMemberCoupon` | UMS | UMS 权益/资产命令、Mapper | TRADE 优惠券管理与统计仍有直接持久化用途；需独立命令/查询切片。 |
| `PosMemberLevel` | UMS | 共享 Mapper 兼容层 | 无新增跨域 Entity 契约。 |
| `OmsOrder` | TRADE | 本域结账、退款、订单查询、报表查询和 Mapper | FIN/HOME 已使用报表快照；订单接口仍是本域 `IService` 兼容面。 |
| `OmsOrderDetail` | TRADE | 本域结账、退款、订单领域服务和 Mapper | 无跨域 Entity 契约。 |
| `OmsOrderLog` | TRADE | 本域结账、退款、订单领域服务和专用 Mapper | 无跨域 Entity 契约。 |
| `OmsOrderPay` | TRADE | 本域结账、退款、订单查询和 Mapper | FIN 使用支付快照；无首切片候选。 |
| `OmsRefundIdempotent` | TRADE | TRADE `RefundStateGuard` 与 TRADE 专用 Mapper，均已使用所有者本地 Entity | **已迁移；不再导入共享 Entity 包。** |
| `OmsDailySummary` | HOME | HOME 快照 writer/query/dashboard 和共享 Mapper | HOME 的自有读模型；不改变刷新或原子写入语义。 |
| `SysBrandConfig` | SYS | SYS 服务/Mapper | `GoodsPosFacade`、GMS Excel 仍直接读取，是明确 SYS 边界后续项。 |
| `SysPrintConfig` | SYS | SYS 服务/Mapper | 打印服务直读是硬件/运行时兼容桥，延后处理。 |
| `SysStrategy` | SYS | SYS 策略服务/Mapper | GMS 周转告警仍使用实体，须以窄策略查询替换。 |
| `Provinces` | SYS | SYS 参考数据服务/Mapper | 无 Feature 消费者；物理迁移会触及 SYS 模块边界，不作为首切片。 |

## `OmsRefundIdempotent` 首切片设计

### 已确认的完整消费面

| 面 | 现状 | AD-2.2 动作 |
| --- | --- | --- |
| 物理表 | `oms_refund_idempotent`，含既有唯一约束语义 | 不改表、不改 Flyway。 |
| Entity | `money-app-api: com.money.entity.OmsRefundIdempotent` | 移到 `money-app-biz` 的 `feature.trade.infrastructure.persistence.entity`。 |
| Mapper | `feature.trade.infrastructure.persistence.mapper.OmsRefundIdempotentMapper` | 仅更新泛型/导入；包和扫描根不变。 |
| 应用调用方 | `RefundStateGuard.acquireIdempotent` | 仅更新导入；保留插入、重复键转换和异常语义。 |
| 事务 | `OmsOrderRefundServiceImpl` 的完整/部分退款事务调用 guard | 不改事务注解、调用顺序或回滚边界。 |
| HTTP/DTO/序列化 | 未发现 Controller、API DTO、JSON、Excel 或跨 Feature 契约引用 | 不新增兼容桥。 |
| Mapper XML | 未发现 XML resultMap、参数类型或 FQCN 引用 | 不改 XML。 |
| 回归 | `CheckoutIntegrationTest` 已覆盖完整和部分退款路径 | AD-2.2 增加重复退款请求断言，并执行既有结账/退款全量回归。 |

### 选择理由与排除项

`OmsRefundIdempotent` 是单一 TRADE 所有者、两个实际生产消费者、无公开序列化面且已有退款事务回归
保护的最小可验证切片。`GmsMemberTransaction` 虽然消费者更少，但当前没有 Feature 应用服务和行为
回归，无法证明迁移后 Mapper 装配及业务语义；`GmsTurnoverWarningSnapshot` 则带有周转快照写入和告警
时序。两者均不比退款幂等切片更适合作为首个有验收价值的迁移。

## AD-2.2 完成证据

- Entity 已从 `money-app-api: com.money.entity` 移至
  `money-app-biz: com.money.feature.trade.infrastructure.persistence.entity`；TRADE Mapper 和 guard 仅更新
  导入，`@TableName`、字段、联合主键语义和 Mapper 扫描根保持不变。
- 新增的重复整单退款回归确认同一 `reqId` 的第二次请求返回 `POS_REFUND_REPEAT`；完整、部分退款与
  全量隔离库回归均通过。
- Java、测试和资源目录中没有旧 FQCN 或 Mapper XML 引用。最终扫描显示共享 Entity 原始扫描从 75
  降至 74 个 Feature 文件；其余共享 Entity 继续为报告指标。

## 已执行的 AD-2.2 验收边界

1. 只移动 `OmsRefundIdempotent` 的物理 Java 包并更新 TRADE Mapper/guard；不得连带移动
   `OmsOrder`、`OmsOrderDetail`、订单 Mapper 或退款 API。
2. `rg` 确认 API 模块不再包含该 Entity，业务源码不再导入 `com.money.entity.OmsRefundIdempotent`，且
   没有旧 FQCN 的 Java/XML/测试引用。
3. 运行重复退款、完整退款、部分退款回归，以及隔离 `money_pos_test` 全量测试、打包、架构门禁和
   `git diff --check`。
4. 在 AD-2.2 验收前，共享 Entity 扫描继续为报告指标；不得把 75 项作为新增失败门禁。
