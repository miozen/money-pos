# AD-2.48：第二十四个共享 Entity 物理归属迁移切片选择

## 结论

选择 SYS `SysBrandConfig` 作为唯一的 AD-2.49 迁移对象；在迁移前先以 API 中立的只读策略契约隔离两个外部消费者。

## 当前盘点

所有权登记册剩余 6 个共享 Entity：`OmsOrder`、`OmsOrderDetail`、`PosCouponRule`、`PosMemberCoupon`、
`SysBrandConfig` 与 `UmsMember`。

`SysBrandConfig` 的持久化所有者明确为 SYS：`SysBrandConfigMapper` 与
`SysBrandConfigServiceImpl` 负责 `sys_brand_config` 的按品牌读取和保存。其仅有的生产外部读取方为：

- 遗留 POS `GoodsPosFacade`：按商品品牌批量读取 `couponEnabled`，用于清空未启用品牌的会员券金额；
- GMS `GmsGoodsExcelManager`：导入商品时读取所有品牌的 `couponEnabled`，决定动态会员券列是否落库。

两者都只需要“品牌 → 是否启用会员券”的读模型，不需要持久化 Entity、Mapper、`levelCodes`、写操作或 SYS
事务。因此，先抽取窄查询契约可消除直接依赖；这比迁移订单、会员、券规则或会员券更小，后者仍涉及结账、退款、资产、打印、报表或跨域桥接。

## AD-2.49 范围与约束

1. 在 API 定义只读品牌券策略查询契约与不可变快照；SYS 提供实现。应同时支持批量品牌 ID 查询和 Excel
   导入所需的全量品牌策略读取，返回 Entity-free 的 `Map<String, Boolean>` 或等价快照集合。
2. `GoodsPosFacade` 与 `GmsGoodsExcelManager` 改依赖该契约，禁止再导入 `SysBrandConfig` 或
   `SysBrandConfigMapper`；随后将 Entity 迁入 `feature.sys.infrastructure.persistence.entity` 并更新 SYS Mapper、
   SYS Service 与测试。
3. 保留 `@TableName("sys_brand_config")`、AUTO ID、品牌、券开关、等级码、租户和审计字段；保留按品牌更新/首次插入、
   POS 券清零、Excel 导入商品与动态价格/券列行为。不得改变路由、DTO、表/Flyway、既有事务边界或业务公式。
4. 新增 SYS 本地 Mapper CRUD 回归，并保留/扩展 `GoodsPosFacadeIntegrationTest` 与 GMS Excel 导入回归，覆盖启用、
   未启用和缺失配置三种策略；再完成隔离 `money_pos_test` 全量测试、打包、扫描夹具、`--check-new` 与空白检查。

## 预期架构结果

迁移完成后，登记册应从 6 降至 5；GMS→SYS 的 `SysBrandConfig` 通配符桥将被窄查询契约取代，已登记跨域 Entity
桥预计从 5 降至 4，通配符基线不扩大。

## 非目标

本选择不移动任何 Entity、Mapper、Service、Controller、DTO 或扫描根；不执行 AD-2.49 的生产改造。
