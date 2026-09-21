# AD-2.58：第二十九个共享 Entity 物理归属迁移切片选择

## 结论

选择 TRADE `OmsOrder` 作为唯一的 AD-2.59 迁移对象。这是登记册中最后一个共享 Entity；其生产
Entity/Mapper 消费者均属于 TRADE，FIN、HOME 与 UMS 已通过既有的 TRADE 读模型契约或 HTTP DTO/VO 取得
所需数据。因此可在不增加跨 Feature 兼容桥的条件下完成最后一次物理归属迁移。

## 当前盘点

`UmsMember` 已在 AD-2.57 迁至 UMS 后，`shared-entity-owners.tsv` 只登记 `OmsOrder → trade`。AD-2.57
后的扫描实测为共享 Feature import 12、所有者本地使用 12、跨域 Entity 桥 0、通配符路径 2。

| 范围 | 盘点事实 | AD-2.59 结论 |
| --- | --- | --- |
| 持久化边界 | `OmsOrderMapper extends BaseMapper<OmsOrder>` 与 `oms_order` 主表 Entity 仍在遗留位置。 | 将 Entity 迁入 `feature.trade.infrastructure.persistence.entity`，Mapper 改用 TRADE 本地类型。 |
| TRADE 写工作流 | 结账上下文/编排、建单与幂等恢复、会员资产扣减、退款状态守卫与资产返还均直接使用订单主对象。 | 逐一更新为本地 Entity，保持结账事务、金额校验、重复单恢复和退款时序。 |
| TRADE 查询与报表投影 | 订单分页/详情、退款、FIN 日支付/瀑布/交班投影和 HOME 订单汇总在 TRADE 内以主订单查询构建 DTO 或不可变快照。 | 保持现有 FIN/HOME 合约和聚合口径；不把 Entity 暴露给其他 Feature。 |
| 对外面 | `/oms-order` 仅接收/返回 DTO、VO；打印消费订单详情 DTO。FIN/HOME 依赖 `Finance*OrderQuery`、`HomeOrderReadQuery` 快照，会员排行是既有读模型。 | 不改路由、JSON 字段、DTO、VO、跨域契约或接口实现归属。 |
| 资源与兼容 | 未发现资源文件保留旧 Entity FQCN；现有两个通配符 import 均在 TRADE 的订单退款/查询实现。 | 移动时将它们收敛为明确的 TRADE 本地 import，不新增通配符。 |

直接类型引用面为 10 个 TRADE 生产类及遗留 `OmsOrderMapper`：结账上下文、资产处理、编排、建单，
订单查询服务，退款资产/状态守卫，以及 FIN/HOME 的三个 TRADE-owned 查询实现。受影响测试夹具覆盖
TRADE 结账、FIN 报表、HOME 快照和 UMS 会员排行；它们只是测试中构造/查询订单，不构成生产跨域依赖。

风险来自主交易聚合的一致性。`OmsOrder` 保留了会员快照、双轨计价、券/满减/手工优惠、成本、实收和最终
销售额；结账必须先校验支付金额并处理唯一单号，退款依赖 `applyPartialRefund`、整单清零与明细退货完成后
升级 `REFUNDED` 的顺序。FIN/HOME 还依赖有效状态、日期边界与退款差额的既有聚合公式。现有结账重复请求、
部分/整单退款、FIN、HOME、会员排行集成回归覆盖这些路径。

## AD-2.59 范围与约束

1. 将 `OmsOrder` 迁入 `feature.trade.infrastructure.persistence.entity`，更新 `OmsOrderMapper`、TRADE
   结账、退款、订单查询、FIN/HOME 报表投影与受影响测试/夹具；删除遗留 API Entity。不得迁移其他 Entity、
   Mapper、Controller、DTO 或扫描根。
2. 保留 `BaseEntity`、`@TableName("oms_order")`、既有 ID 行为，以及订单号、会员/联系方式、状态、地址、
   成本、零售价/会员价/特权、会员券/免收、兼容总价/券额、实收/最终销售、备注、支付/完成时间、租户、满减券
   和整单优惠全部字段，并保持表及 Flyway 定义。
3. 保留 `/oms-order` 全部路由与 DTO/VO、打印订单详情、结账金额校验和重复请求恢复、库存/会员资产/支付的
   既有事务时序、部分退款原子金额更新、整单退款清零与完成状态升级，以及 FIN/HOME/会员排行既有查询口径和
   TRADE→FIN/HOME、TRADE→UMS 契约。
4. 新增 TRADE 本地 Mapper CRUD 回归，覆盖显式表映射、BaseEntity/ID、全部订单快照字段、更新和删除；保留/扩展
   结账、部分/整单退款、订单列表/详情、FIN、HOME、会员排行回归。完成隔离 `money_pos_test` 全量测试、打包、
   扫描夹具、`--check-new` 与空白检查。
5. 从登记册移除最后一项，并更新扫描夹具以验证“无共享 Entity / 无新跨所有者 Entity”场景；两个既有通配符
   import 应收敛而非扩大，不引入新的桥。

## 预期架构结果

迁移完成后，所有权登记册降至 0，共享 Feature Entity import 与所有者本地共享使用降至 0，跨域 Entity 桥
维持 0；现有两个通配符路径应收敛为 0。扫描仍以 additions-only 方式运行，不把其他历史报告项改为失败规则。

## 非目标

本选择不移动任何 Entity、Mapper、Service、Controller、DTO 或扫描根；不执行 AD-2.59 的生产改造。
