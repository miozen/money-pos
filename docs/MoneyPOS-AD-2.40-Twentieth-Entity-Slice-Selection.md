# AD-2.40 第二十个共享 Entity 物理归属迁移切片选择

## 结论

选择 **GMS `GmsStockLog`**，后续唯一实施任务为 **AD-2.41**：将它从 API 共享包迁至
`feature.gms.infrastructure.persistence.entity`，同步遗留 Mapper、GMS 库存命令/查询/分析与本域 Controller、旧
服务及测试导入，并补库存流水 Mapper 回归。

## 固定边界

- 保持隐式 `gms_stock_log` 表映射、AUTO ID、所有数量/成本/资产/订单/备注/操作人/时间/租户字段、空值与类型；
  不改 Flyway、索引、库存流水写入顺序或成本快照公式。
- `GmsInventoryDocServiceImpl`、盘点、入库、销售/退款库存命令、查询、库存分析和遗留服务仍使用 GMS 本地类型；
  不改变库存、成本、流水、事务、分页或筛选语义。
- `/gms/stockLog` 保持路由、查询 DTO、`PageVO`、JSON 字段和分页行为，仅把 Controller/查询服务的本域泛型改为
  本地实体；不得引入 DTO 重写或跨域 Entity 契约。
- 新增 Mapper CRUD 回归，保留库存单、流水筛选、结账/退款和库存分析回归；运行隔离全量、打包、扫描与空白检查。

`GmsGoods` 仍有更宽目录/库存/HTTP 面；UMS 券与会员有资产/TRADE 桥；SYS 有 GMS 桥；订单主从承担核心交易。
故 `GmsStockLog` 是余下 10 项中下一最小 GMS 所有者切片。预计登记 **10→9**，桥（6）和通配符（3）不得扩大。

## 下一步

**AD-2.41：迁移 GMS `GmsStockLog` 到 GMS 持久化实体包。**
