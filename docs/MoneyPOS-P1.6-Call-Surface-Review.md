# MoneyPOS P1.6：调用面复核与门禁策略

## 结论

P1.6 已闭环：P1 范围内已完成场景不再泄露跨域持久化实现；库存写侧、结算试算权益读取、POS 会员展示和 UMS 会员档案/Excel 品牌选择已分别在 P1.6.2 至 P1.6.5 收敛。共享 Entity 导入从 P1.0 的 65 个变为 69 个；这个指标仍只作追踪，不能直接作为阻断条件。

## 已关闭的 P1 场景

| 场景 | API 契约 | 调用方状态 |
| --- | --- | --- |
| 结账商品校验/试算输入 | `CheckoutGoodsQuery` / `CheckoutGoodsSnapshot` | TRADE 不读取 GMS 商品或价格 Entity/Mapper |
| POS 商品目录 | `PosGoodsCatalogQuery` / `PosGoodsCatalogSnapshot` | TRADE 不读取 GMS 商品或价格 Entity/Mapper |
| 结账会员、订单/收银档案 | `MemberCheckoutQuery`、`MemberOrderProfileQuery`、`MemberPosProfileQuery` | TRADE 不读取 `UmsMember` 或会员服务 Entity 返回值 |
| 会员未使用券汇总 | `MemberCouponCountQuery` | UMS 导出不读取 TRADE 券 Mapper |
| 结算、退款会员资产 | `MemberSettlementCommand`、`MemberRefundCommand` | TRADE 门面只组装命令；UMS 写入资产、券、余额和日志 |
| 结算试算会员权益 | `CheckoutPricingBenefitQuery` / `CheckoutPricingBenefitSnapshot` | TRADE 试算只消费会员品牌等级映射和满减规则金额，不读取 UMS Mapper 或 Entity |
| POS 会员权益与品牌展示 | `PosMemberBenefitQuery` / `PosMemberBenefitSnapshot`、`BrandNameQuery` | TRADE POS 只消费未使用券、券规则及品牌 ID→名称快照，不读取 UMS Mapper/Entity 或 GMS 实现服务 |

## 尚未关闭的调用面

| 优先级 | 调用面 | 当前依赖 | 收敛方案 | 验收 |
| --- | --- | --- | --- | --- |
| 已完成 P1.6.2 / P1.4c | TRADE→GMS 销售扣库存、退款回库、套餐穿透、库存流水和单据 | 已移除 `GoodsStockFacade` 的 GMS Entity/Mapper/Service 与 `PosInventoryActionService` | `SaleStockCommand` / `RefundStockCommand` 由 GMS 处理；TRADE 仅传库存行快照与订单号，继续加入外层结账/退款事务 | 阶段 0 结账、全/部分退款、套餐、库存不足和并发库存回归 |
| 已完成 P1.6.3 | TRADE 结算试算 | 已移除 `PosCalculationEngine` 对会员品牌等级、满减券规则 Mapper 的读取 | `CheckoutPricingBenefitQuery` 由 UMS 组装品牌等级映射和满减规则门槛/优惠额；TRADE 只消费快照 | 权益查询、阶段 0 结账和全量测试回归 |
| 已完成 P1.6.4 | TRADE POS 会员展示 | 已移除 `PosServiceImpl` 对券规则/券 Mapper 及 GMS 品牌服务/Entity 的读取 | UMS 提供 `PosMemberBenefitQuery`，GMS 提供 `BrandNameQuery`；TRADE 保留中文展示和既有接口字段 | POS 会员搜索、券数量/规则、品牌等级展示与券规则接口回归 |
| 已完成 P1.6.5 | UMS 会员档案/模板/导出/导入品牌展示 | 已移除 UMS 对 GMS 品牌服务和 Mapper/Entity 的读取 | `BrandSelectionQuery` 由 GMS 提供 ID/名称快照；档案按 ID 查名称，模板、导出和导入共享品牌选择 | 会员档案、导入模板、资产导出与真实工作簿导入回归 |
| P2（不纳入 P1 完成条件） | FIN/HOME 报表读模型 | 订单、库存、会员 Entity/Mapper 与直接 Feature 服务 | 按 Entity 归属表的 P2 报表快照拆分 | FIN/HOME 集成测试和页面回归 |

## 门禁策略

`scripts/architecture-scan.sh --check-new` 继续保持“仅阻止新增”的原则，现有三类结构规则不变，并新增“跨 Feature 实现包 import”检查。

当前 8 项历史基线允许继续存在，分别属于 FIN→UMS、HOME→GMS、TRADE→FIN、TRADE→GMS 和 UMS→GMS。任何新的 `com.money.feature.<other-feature>...` import 均失败；跨域协作必须优先新增 `money-app-api` 中的场景契约。该规则不以共享 Entity 导入数量作为阻断条件，避免误伤归属 Feature 内部实现。

## Maven 物理拆分复核

P1 的跨域场景已收敛，但当前仍不自动进行 Maven 物理拆分：单体事务仍跨订单、库存与会员资产，且 P2 报表读模型尚未独立评估。只有循环依赖、独立编译收益和 Spring 装配验证同时具备，才另行决定是否拆分。

## 下一最小任务

**P1 已闭环。下一最小任务：P2 FIN/HOME 报表读模型调用面盘点。**
