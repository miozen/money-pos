# AD-2.44 第二十二个共享 Entity 物理归属迁移切片选择

选择 **GMS `GmsGoods`**；后续唯一实施任务为 **AD-2.45**：将它迁至 `feature.gms.infrastructure.persistence.entity`，更新遗留 Mapper、GMS 商品/库存/分析/Excel 消费者和测试。

保持商品表映射、ID、价格/库存/成本/状态/品牌分类字段、库存事务、Excel、结账/POS/FIN 的既有 Entity-free 快照、路由/DTO 与 Flyway 不变。必须新增 Mapper CRUD，并回归商品核心、库存单、Excel、结账、POS 搜索和财务快照。订单、会员主档、券及 SYS 配置仍有核心事务或跨所有者桥，故延后。预计登记 8→7，桥（6）和通配符（3）不得扩大。

下一步：**AD-2.45：迁移 GMS `GmsGoods` 到 GMS 持久化实体包。**
