# MoneyPOS P1：跨域契约收敛清单

## 目标与边界

P1 在不拆 Maven 模块、不移动共享 Entity 物理包、不改数据库、Flyway、路由或既有事务所有权的前提下，逐步消除 TRADE 对 GMS/UMS 的 `IService<Entity>`、Entity 和跨域 Mapper 依赖。目标是让跨域调用只表达所需场景，而不是泄露对方持久化实现。

每个切片只迁移一个场景；先做只读，再做 POS 聚合，最后才处理结算/退款写侧。涉及结算、券核销或退款时，必须运行阶段 0 `CheckoutIntegrationTest` 和全量测试。

## 实施顺序

- [x] P1.0 已建立契约收敛清单：以阶段 4 Entity 归属表和阶段 5 模块拆分决策为输入；当前 65 个共享 Entity 导入仅作追踪，不将“减少数量”当作单独目标。
- [x] P1.1 已迁移会员未使用满减券计数：新增 API 中立接口 `MemberCouponCountQuery`，TRADE 保留其实现和 `PosMemberCouponMapper`，UMS 会员资产导出只依赖中立接口。该契约只返回会员 ID→数量的聚合，不暴露 Entity/Mapper；导出工作簿回归测试覆盖 `UNUSED`/`USED` 过滤。
- [x] P1.2 已迁移收银会员核验：新增 API 中立 `MemberCheckoutSnapshot(id, name, phone)` 和 `MemberCheckoutQuery`；UMS 使用本域 Mapper 组装有效会员快照，TRADE 的 `CheckoutValidationService`、`CheckoutContext`、订单归档和资产请求只依赖该快照，不再持有 `UmsMember` 或调用 `UmsMemberService.getById()`。结账集成测试覆盖会员订单的 ID、姓名、手机号归档。
- [x] P1.3 已迁移订单会员档案与 POS 会员查询：新增 `MemberOrderProfileSnapshot/Query` 和 `MemberPosProfileSnapshot/Query`。UMS 负责组装订单展示/小票所需档案（含会员券余额）、POS 搜索及品牌等级矩阵；TRADE 不再调用 `UmsMemberService.getDetail()`/`lambdaQuery()`，也不再使用 `UmsMemberBrandLevelMapper`。订单详情与 POS 搜索回归覆盖会员身份、余额和等级矩阵。
- [ ] P1.4 迁移商品/价格只读边界：
  - [x] P1.4a 结账读侧：新增 `CheckoutGoodsSnapshot/Query`，由 GMS 组装商品主档、分类名、可售库存和会员价矩阵；结账核验、试算、订单快照和库存扣减命令准备不再读取 `GmsGoodsService`、`GmsGoods` 或价格 Mapper。库存写命令与事务顺序保持不变。
  - [x] P1.4b POS 商品目录：新增 `PosGoodsCatalogSnapshot/Query`，GMS 负责条码、名称、助记码搜索及价格矩阵组装；TRADE 的 `PosService` 仅转换快照为既有 `PosGoodsVO`，不再读取 `GmsGoodsService`、`GmsGoods` 或 `PosSkuLevelPriceMapper`。
  - [ ] P1.4c 库存写侧：在结算/退款命令事务特征固化后，再收敛 `GoodsStockFacade` 内部的 GMS 写入实现；不与只读迁移混做。
- [ ] P1.5 会员资产结算、退款命令：详见 `MoneyPOS-P1.5-Member-Asset-Command-Checklist.md`。
  - [x] P1.5.1 已完成写侧盘点与特征基线。
  - [ ] P1.5.2 补齐部分退款余额与券并发回滚特征测试。
  - [ ] P1.5.3–P1.5.5 定义并迁移结算/退款中立命令。
  - [ ] P1.5.6 全量验证与并发复核。
- [ ] P1.6 复核所有 P1 调用面、更新 Entity 归属表和架构扫描策略；仅在无循环、独立编译收益和运行时装配验证均满足时，重新评估 Maven 物理拆分。

## 当前完成定义

P1 不要求消灭所有共享 Entity 导入。完成的标志是：已识别的 TRADE↔UMS/GMS 跨域场景均改为场景 DTO/命令/查询契约，TRADE 不再通过对方 Entity、Mapper 或 `IService<Entity>` 完成这些场景；收银和退款事务回归保持通过。

下一最小任务是 **P1.5.2：补齐部分退款余额与券并发回滚特征测试**。
