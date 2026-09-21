# AD-2.38 第十九个共享 Entity 物理归属迁移切片选择

## 结论

选择 **GMS `GmsBrand`** 作为第十九个切片；后续唯一实施任务为 **AD-2.39**：将它从
`money-app-api: com.money.entity` 迁至 `feature.gms.infrastructure.persistence.entity`，更新遗留 Mapper、GMS
品牌服务/名称选择查询、商品 Excel 消费者和测试，并补 Mapper 持久化回归。

## 固定边界

- `GmsBrand extends BaseEntity` 保持显式 `gms_brand`、既有 ASSIGN_ID、`logo`、`name`、`description`、
  `goodsCount`、`tenantId` 及继承审计字段；Flyway 种子、非 AUTO ID、默认空 logo/0 商品数不变。
- Mapper 包/扫描根不动。`GmsBrandService` 的本域 `IService<GmsBrand>`、同名校验、分页排序、上传回滚清理、
  提交后旧 logo 删除、删除时序和原子商品数更新不得改变。
- `BrandNameQueryService` 的 ID→名称和品牌选择快照、Excel 的品牌下拉/导出/导入名称匹配保持不变。Controller
  仍只使用 DTO/VO/`SelectVO`；UMS/TRADE/FIN 继续使用既有 Entity-free 契约，不得新增跨域 Entity import。
- 必须新增 `GmsBrandMapperIntegrationTest`，覆盖本地实体的 ID、全部字段、继承审计字段、更新/删除；保留 GMS
  核心、商品 Excel、UMS 会员导入/导出/POS 和 FIN 名称快照回归，并运行隔离全量、打包、扫描与空白检查。

## 候选比较

`GmsGoods`/`GmsStockLog` 仍有核心目录、库存命令或旧服务面；UMS 券/会员有资产与 TRADE 桥；SYS 仍有 GMS→SYS
桥；订单主从表仍承担结账、退款、报表、打印。因此 `GmsBrand` 是余下 11 项中无生产跨域实体外泄、边界最小的候选。

预期登记 **11→10**；共享 import、owner-local uses 以迁移后扫描实测为准，桥（6）与通配符（3）不得扩大。

## 下一步

**AD-2.39：迁移 GMS `GmsBrand` 到 GMS 持久化实体包。**
