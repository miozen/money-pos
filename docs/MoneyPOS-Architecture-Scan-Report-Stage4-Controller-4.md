# MoneyPOS 阶段 4 控制器迁移收口报告：剩余 4 项

本报告记录 `PosCouponRuleController` 迁移后的当前快照，不修改 v1 初始基线。

## 本次切片

- `PosCouponRuleController` 已改为依赖 `CouponRuleManagementService`，不再直接导入 `PosCouponRuleMapper` 或 `PosMemberCouponMapper`。
- 服务保留规则分页、名称筛选、创建、更新、删除，以及会员未使用券按规则汇总的原有行为。
- `/pos/couponRule` 路由、管理端 Entity 请求/返回、分页排序和会员券聚合 map 字段未改变。

## 验证与扫描

| 项目 | 结果 |
| --- | --- |
| 控制器集成测试 | `PosCouponRuleControllerIntegrationTest` 通过。 |
| 全量 `mvn test` | 12 个测试类、26 个用例通过；0 failures / 0 errors。 |
| `mvn package -DskipTests` | 通过。 |
| additions-only 门禁 | 通过。 |
| Controller 直接导入 Mapper | 4（初始基线 7）。 |
| 跨 Feature `ServiceImpl` / Mapper 导入 | 0。 |
| `platform → feature` 导入 | 0。 |
| Feature 导入共享 `com.money.entity` | 65；其中本切片新增 2 项为 TRADE 本域服务内部实现，非跨域 API 契约。 |

## 剩余项与下一步

剩余 Controller-Mapper 文件为 `GmsBrandConfigController`、`GmsGoodsExcelController`、`UmsMemberController` 和 `UmsMemberImportController`。下一最小任务是先盘点它们各自的服务、Mapper 和路由调用面，优先选择依赖最窄且不会与商品 Excel 或会员导入共享批处理逻辑混杂的切片。
