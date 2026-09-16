# MoneyPOS 阶段 4 P0 优惠券 DTO 收口报告

本报告记录 P0 优惠券规则契约切片完成后的架构快照；它不覆盖初始基线或阶段 4.5 报告。

## 已完成的契约收敛

- `PosService.getValidCouponRules()` 不再返回 `PosCouponRule` 持久化实体，而是返回 `CouponRuleSummary`。
- `/ums/member/coupon-rules` 保持既有路由与 JSON 字段名，返回 `id`、`name`、`thresholdAmount`、`discountAmount`、`status` 五个展示所需字段，不泄漏创建人、更新时间、租户等持久化元数据。
- `UmsMemberPosControllerIntegrationTest` 在隔离的 `money_pos_test` 中验证该端点返回 DTO 的名称、门槛、优惠额和状态。

## 验证结果

| 项目 | 结果 |
| --- | --- |
| 全量 `mvn test` | 11 个测试类、25 个用例通过；0 failures / 0 errors。 |
| `mvn package -DskipTests` | 通过。 |
| Controller 直接导入 Mapper | 5（初始基线 7；无新增）。 |
| 跨 Feature `ServiceImpl` / Mapper 导入 | 0。 |
| `platform → feature` 导入 | 0。 |
| Feature 导入共享 `com.money.entity` | 63（兼容债务统计，非阻断）。 |

## 门禁结论和下一步

`SysStrategyController`、`GmsStockLogController` 两个稳定 Controller 迁移，以及 P0 DTO 契约均已完成。因此，前三项结构规则已具备 additions-only（仅阻止新增违规）比较门禁的前提：历史基线仍允许存在，但任何新文件或新依赖不得增加违规。

共享 Entity 引入仍只报告、不阻断；其静态归属需要在 P1 商品/会员窄读 DTO 切片后再进一步收紧。下一最小任务是为前三项规则实现基线比较和失败退出码，并先以本地验证方式运行，不立即接入 Maven 或 CI。
