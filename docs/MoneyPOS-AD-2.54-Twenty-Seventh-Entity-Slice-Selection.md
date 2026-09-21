# AD-2.54：第二十七个共享 Entity 物理归属迁移切片选择

## 结论

选择 TRADE `OmsOrderDetail` 作为唯一的 AD-2.55 迁移对象。它是余下三个 Entity 中唯一没有生产跨
Feature Entity 泄露、且无需先引入新契约的最小物理归属切片。

## 当前盘点

`PosCouponRule` 已在 AD-2.53 迁至 UMS 后，所有权登记册剩余三个共享 Entity：`OmsOrder`、
`OmsOrderDetail` 与 `UmsMember`。

| Entity | 所有者 | 生产面与暂缓/选择结论 |
| --- | --- | --- |
| `OmsOrder` | TRADE | 结账、退款、订单查询、打印，以及 TRADE 向 FIN/HOME 提供的多种报表快照均依赖订单主表；主交易聚合与兼容查询面最宽，暂缓。 |
| `OmsOrderDetail` | TRADE | Mapper、订单明细域服务、结账建单/扣库存、退款返库、订单详情和遗留打印均为 TRADE 所有者面；Controller 和外部报表消费 DTO/快照，不直接暴露 Entity。选定。 |
| `UmsMember` | UMS | 会员档案、导入、资产、日志、充值、结账、FIN/HOME 查询和公开管理接口共同依赖，且公开/兼容面最广，暂缓。 |

`OmsOrderDetail` 的所有者明确为 TRADE。`OmsOrderDetailMapper`、`OmsOrderDetailService`、结账编排、退款库存
帮助器和订单查询在同一 Feature 内使用它；遗留 `PosPrinterService` 同样只通过 TRADE 所有者服务/DTO 输出。
未发现其他 Feature 生产代码对该 Entity 或 Mapper 的直接依赖，也未发现资源文件中保留其旧 FQCN，因此本切片
不应借机增加契约或调整扫描兼容桥。

其风险来自交易一致性，而非跨域边界：结账需要构建商品、价格、成本、品牌/分类快照并写入订单明细，退款依赖
`returnQuantity` 的原子守卫和状态转换，库存扣减/返库以及订单详情、打印和 FIN/HOME 的既有查询都必须维持
原有时序与计算口径。现有结账/部分退款/全额退款、FIN、HOME 与会员排行集成测试覆盖这些路径。

## AD-2.55 范围与约束

1. 将 `OmsOrderDetail` 迁入 `feature.trade.infrastructure.persistence.entity`，更新遗留 Mapper、TRADE
   明细服务、结账、退款、订单查询、打印消费者及受影响测试；不得改动其他 Entity 或扫描根。
2. 保留 `BaseEntity`、`@TableName("oms_order_detail")`、既有 ID 行为、订单号/状态/商品/价格/数量/成本快照、
   券、退货、租户、品牌/分类历史快照字段，以及表/Flyway 定义。
3. 保留结账重复请求恢复、库存扣减、`refundGoodsAtomically` 的剩余数量守卫与 `REFUNDED` 状态转换、整单/部分
   退款返库、订单详情 DTO、打印和 FIN/HOME/会员排行查询口径；不得改变路由、DTO、事务边界或业务公式。
4. 新增 TRADE 本地 Mapper CRUD 回归，覆盖明细的显式表映射、BaseEntity/ID、全部业务快照字段、更新与删除；
   保留/扩展结账、部分/全额退款、订单查询、打印、FIN/HOME 与会员排行回归；再完成隔离
   `money_pos_test` 全量测试、打包、扫描夹具、`--check-new` 与空白检查。

## 预期架构结果

迁移完成后，登记册应从 3 降至 2。当前跨域 Entity 桥已为 0，仍应保持 0；两个通配符路径不得扩大。

## 非目标

本选择不移动任何 Entity、Mapper、Service、Controller、DTO 或扫描根；不执行 AD-2.55 的生产改造。
