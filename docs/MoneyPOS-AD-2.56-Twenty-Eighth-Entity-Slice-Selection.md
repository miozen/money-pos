# AD-2.56：第二十八个共享 Entity 物理归属迁移切片选择

## 结论

选择 UMS `UmsMember` 作为唯一的 AD-2.57 迁移对象。它的持久化 Entity/Mapper 消费者均在 UMS，TRADE、FIN
与 HOME 已通过既有 Entity-free 查询/命令契约取得所需数据；因此可在不新增跨域兼容桥的前提下完成物理迁移。

## 当前盘点

`OmsOrderDetail` 已在 AD-2.55 迁至 TRADE 后，所有权登记册只剩 `OmsOrder` 与 `UmsMember`。

| Entity | 所有者 | 生产面与选择结论 |
| --- | --- | --- |
| `OmsOrder` | TRADE | 结账、退款、订单查询、打印和多个 TRADE→FIN/HOME 报表读模型都依赖主订单；主交易聚合与兼容/报表查询面最宽，保留为最后一个切片。 |
| `UmsMember` | UMS | UMS Mapper、档案、导入、资产、日志、充值、结账查询、FIN/HOME 读模型实现和公开管理接口共同使用；外部 Feature 已消费 Entity-free 契约或 DTO，选定。 |

`UmsMember` 的持久化所有者明确为 UMS。`UmsMemberMapper`、会员档案/导入、资产结算/充值、会员日志、
POS/结账档案和 FIN/HOME 读模型实现均在 UMS 内；`/ums/member` 接口已使用 DTO/VO，不直接把 Entity
作为 HTTP 契约。TRADE、FIN 与 HOME 分别依赖已有的会员结算命令、结账/订单档案、资产、日统计等窄查询
契约，而非 Entity 或 Mapper。未发现资源文件中残留旧 FQCN，也没有登记的跨域 Entity 桥。

该切片的风险是 UMS 内部工作流完整性：会员的卡号/档案、逻辑删除、等级、余额、本金券和消费统计必须保持；
导入、资产 Excel、充值/红冲、结账余额扣减、退款恢复、满减券、会员日志、POS 搜索、排行榜与 FIN/HOME
快照均不得改变。现有会员导入、资产、充值、结账/退款、POS、排行、FIN/HOME 集成回归覆盖这些边界。

## AD-2.57 范围与约束

1. 将 `UmsMember` 迁入 `feature.ums.infrastructure.persistence.entity`，更新遗留 Mapper、UMS 档案、导入、
   资产、日志、充值、查询和 Controller 同域消费者，以及受影响测试/夹具；不得改动 `OmsOrder` 或扫描根。
2. 保留 `BaseEntity`、`@TableName("ums_member")`、既有 ASSIGN_ID 行为、卡号/姓名/类型/联系方式/地址、
   券、消费/取消统计、备注、最后到店、逻辑删除、租户、等级和余额字段，以及表/Flyway 定义。
3. 保留 `/ums/member` 路由/DTO/VO、逻辑删除筛选、会员导入、资产 Excel、充值和红冲、余额原子扣减、
   结账/退款资产、券与日志、POS 搜索、排行榜和 FIN/HOME 快照口径；不得改变事务边界、业务公式或既有跨域契约。
4. 新增 UMS 本地 Mapper CRUD 回归，覆盖显式表映射、BaseEntity/ID、全部档案与资产字段、更新和删除；
   保留/扩展会员管理、导入、资产、充值、结账/退款、POS、排行、FIN/HOME 回归；再完成隔离
   `money_pos_test` 全量测试、打包、扫描夹具、`--check-new` 与空白检查。

## 预期架构结果

迁移完成后，登记册应从 2 降至 1。跨域 Entity 桥须维持 0，当前两个通配符路径不得扩大。

## 非目标

本选择不移动任何 Entity、Mapper、Service、Controller、DTO 或扫描根；不执行 AD-2.57 的生产改造。

## AD-2.57 实施结果

`UmsMember` 已迁至 `feature.ums.infrastructure.persistence.entity`。遗留 Mapper、UMS 档案、导入、资产、
日志、充值、查询和受影响测试/夹具均已使用本地 Entity；新增 UMS Mapper CRUD 回归覆盖 BaseEntity/ID、
全部档案与资产字段、更新和删除。会员路由/DTO/VO、逻辑删除、导入、资产、充值红冲、结账退款、券/日志、
POS/排行、FIN/HOME、表/Flyway 和事务语义未变。
