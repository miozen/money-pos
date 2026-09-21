# AD-2.42 第二十一个共享 Entity 物理归属迁移切片选择

选择 **UMS `UmsMemberLog`**；后续唯一实施任务为 **AD-2.43**：将该实体迁至
`feature.ums.infrastructure.persistence.entity`，更新遗留 Mapper、会员资产/充值/结算/导入、同域日志服务/Controller、
FIN 快照读取和受影响测试，并补 Mapper 持久化回归。

保持显式 `ums_member_log`、AUTO ID、会员/余额券/操作/前后金额/订单/实收金额/会员历史快照/审计租户字段、资产事务、
分页 JSON、FIN Entity-free 快照、表/Flyway 不变。不得引入 TRADE/GMS Entity 依赖。`GmsGoods`、订单主从、券、会员主档
拥有更宽核心或跨域面；SYS 配置仍有 GMS 桥，因此延后。预计登记 **9→8**，桥（6）与通配符（3）不得扩大。

下一步：**AD-2.43：迁移 UMS `UmsMemberLog` 到 UMS 持久化实体包。**
