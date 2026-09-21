# AD-2.46 第二十三个共享 Entity 物理归属迁移切片选择

选择 **SYS `SysStrategy`**；后续唯一实施任务为 **AD-2.47**：将其迁至 `feature.sys.infrastructure.persistence.entity`，更新遗留 Mapper、SYS 策略服务/Controller、FIN 查询、已登记的 GMS 周转兼容桥和测试。

保持显式 `sys_strategy`、AUTO ID、所有客流/周转/死库存/周期分析/租户字段、默认值、策略计算、固定配置读取、路由/JSON、Flyway 不变。GMS→SYS 读取是既有登记桥，迁移只能同步本地 FQCN，桥数不得扩大；不得借此改造成新的跨域契约。新增 Mapper CRUD 回归，保留 SYS 策略、FIN 客流和 GMS 周转回归。其余订单、会员、券及品牌配置具有更宽核心事务或桥面，因此延后。预计登记 7→6，桥 6、通配符 3 不变。

下一步：**AD-2.47：迁移 SYS `SysStrategy` 到 SYS 持久化实体包。**
