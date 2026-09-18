# AD-3.2 跨域 `IService<Entity>` 实际调用盘点

## 范围与方法

以当前生产 Java 源码为准，盘点所有 `extends IService<Entity>` 接口，并搜索其注入点和
实际调用的方法；测试、实现类自身和同一 Feature 内调用不计为跨域泄露。这里的“泄露”是指
消费者可调用 MyBatis-Plus 继承的实体读写能力，或接收另一所有者的持久化 Entity。

共发现 20 个接口：GMS 4、UMS 2、TRADE 3、SYS/平台 9、遗留业务服务 2。

## 所有者矩阵

| 所有者 | `IService<Entity>` 接口 | 跨域生产调用 | 结论 |
| --- | --- | --- | --- |
| GMS | `GmsGoodsService`、`GmsBrandService`、`GmsGoodsCategoryService`、`GmsInventoryOrderService`、`GmsStockLogService` | `GoodsPosFacade` 调用 `GmsGoodsService.lambdaQuery()` 并取得 `GmsGoods`。 | **真实泄露 1**：POS 遗留外观具有 GMS 实体查询能力；其余均本域。 |
| UMS | `UmsMemberService`、`UmsMemberLogService` | 无。 | 无跨域 `IService` 调用；TRADE/HOME/FIN 已使用窄查询契约。 |
| TRADE | `OmsOrderService`、`OmsOrderDetailService`、`OmsOrderLogService` | 无。 | 无跨域 `IService` 调用；服务均由 TRADE 控制器或本域结算/退款流程消费。 |
| SYS | `SysDictService`、`SysDictDetailService`、`SysPermissionService`、`SysRoleService`、`SysRolePermissionRelationService`、`SysTenantService`、`SysUserService`、`SysUserRoleRelationService` | `SysDictDetailService.listByDict()` 向 TRADE/GMS/UMS 返回 `List<SysDictDetail>`。 | **真实泄露 2**：字典持久化实体越过 SYS 边界；其余仅 SYS 本域。 |
| 遗留业务服务 | `ProvincesService`、`SysPrintConfigService` | 无。 | 仅由各自旧控制器消费；不属于本轮跨 Feature 泄露。 |

## 真实调用明细

### GMS 商品到 POS 遗留外观

`GoodsPosFacade` 服务于 `/gms/goods/pos-search`，直接以 `GmsGoodsService.lambdaQuery()` 查询
并读取 `GmsGoods`，之后才组装 `GmsGoodsVO`。虽然最终 HTTP 输出不是实体，但 POS 消费者
拥有 GMS 的通用实体查询面，违反了窄契约目标。

该问题应单列为后续切片：由 GMS 提供只读 POS 商品搜索快照/查询契约，POS 只组装其策略字段；
不得把 `lambdaQuery()` 或 `GmsGoods` 继续暴露给 POS。

### SYS 字典到业务 Feature

以下生产调用从 `SysDictDetailService.listByDict()` 接收 `SysDictDetail`：

- TRADE：支付渠道名称解析、订单状态显示、POS 会员类型显示；
- GMS：商品 Excel 中会员类型显示；
- UMS：会员档案类型显示、Excel 模板类型显示。

这些调用只消费 `value -> cnDesc` 映射。`SysDictDetailService` 已提供
`getValueToCnDescMap(String)`，因此无需新增 Entity 契约或更改数据表。该切片优先级高于 POS
商品搜索：替换范围明确、读取语义已存在且不涉及商品/价格策略。

## 不在本轮处理的项

- 同一 Feature 内使用 `IService` 的实体读写是本域持久化实现，不能按接口继承关系机械删除。
- `SysDictDetailService` 的管理控制器仍可在 SYS 自己的边界使用实体；AD-3.3 只替换业务
  Feature 的跨域读取。
- 本文不迁移共享 Entity 的物理包；那仍是 AD-2 的职责。

## 结论与下一步

AD-3.2 已确认没有 UMS 或 TRADE 的跨 Feature `IService<Entity>` 实际调用；剩余两处风险均已
定级。下一最小任务是 **AD-3.3：将业务 Feature 的 SYS 字典实体读取迁移到既有
`value -> cnDesc` 查询契约**。随后再单独设计 GMS → POS 商品搜索快照，避免把两个业务口径
混为一次改造。
