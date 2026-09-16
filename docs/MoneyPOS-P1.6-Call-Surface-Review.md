# MoneyPOS P1.6：调用面复核与门禁策略

## 结论

P1.5 的会员资产命令已经闭环，但 P1 不能因而整体关闭。复核确认：已完成场景不再泄露跨域持久化实现；仍有一组库存写侧和三组历史兼容调用需要继续收敛。共享 Entity 导入从 P1.0 的 65 个变为 68 个，原因是 UMS 资产命令处理器成为本域实现；这个指标仍只作追踪，不能直接作为阻断条件。

## 已关闭的 P1 场景

| 场景 | API 契约 | 调用方状态 |
| --- | --- | --- |
| 结账商品校验/试算输入 | `CheckoutGoodsQuery` / `CheckoutGoodsSnapshot` | TRADE 不读取 GMS 商品或价格 Entity/Mapper |
| POS 商品目录 | `PosGoodsCatalogQuery` / `PosGoodsCatalogSnapshot` | TRADE 不读取 GMS 商品或价格 Entity/Mapper |
| 结账会员、订单/收银档案 | `MemberCheckoutQuery`、`MemberOrderProfileQuery`、`MemberPosProfileQuery` | TRADE 不读取 `UmsMember` 或会员服务 Entity 返回值 |
| 会员未使用券汇总 | `MemberCouponCountQuery` | UMS 导出不读取 TRADE 券 Mapper |
| 结算、退款会员资产 | `MemberSettlementCommand`、`MemberRefundCommand` | TRADE 门面只组装命令；UMS 写入资产、券、余额和日志 |

## 尚未关闭的调用面

| 优先级 | 调用面 | 当前依赖 | 收敛方案 | 验收 |
| --- | --- | --- | --- | --- |
| P1.6.2 / P1.4c | TRADE→GMS 销售扣库存、退款回库、套餐穿透、库存流水和单据 | `GoodsStockFacade`、`PosInventoryActionService` 直接使用 GMS Entity/Mapper/Service | 新增 GMS 所有的销售扣减、退款回库命令及处理器；TRADE 仅传库存行快照与订单号，保持外层结账/退款事务 | 阶段 0 结账、全/部分退款、套餐、库存不足和并发库存回归 |
| P1.6.3 | TRADE 结算试算 | `PosCalculationEngine` 直接读取会员品牌等级、满减券规则 Mapper | UMS 提供试算所需的会员权益/满减规则快照查询 | 会员价、会员券、满减门槛和异常规则回归 |
| P1.6.4 | TRADE POS 会员展示 | `PosServiceImpl` 直接读取券规则/券 Mapper，且从 GMS 服务取得品牌 Entity | UMS 提供 POS 会员权益展示快照；GMS 提供品牌 ID→名称窄查询 | POS 会员搜索、券数量/规则、品牌等级展示回归 |
| P1.6.5 | UMS 会员档案/模板品牌展示 | `UmsMemberProfileService`、模板/导出服务直接调用 GMS 品牌服务或读取品牌 Entity | GMS 提供品牌选择 DTO/查询；保留 UMS 本域权益实现 | 会员列表、导入模板、资产导出工作簿回归 |
| P2（不纳入 P1 完成条件） | FIN/HOME 报表读模型 | 订单、库存、会员 Entity/Mapper 与直接 Feature 服务 | 按 Entity 归属表的 P2 报表快照拆分 | FIN/HOME 集成测试和页面回归 |

## 门禁策略

`scripts/architecture-scan.sh --check-new` 继续保持“仅阻止新增”的原则，现有三类结构规则不变，并新增“跨 Feature 实现包 import”检查。

当前 9 项历史基线允许继续存在，分别属于 FIN→UMS、HOME→GMS、TRADE→FIN、TRADE→GMS 和 UMS→GMS。任何新的 `com.money.feature.<other-feature>...` import 均失败；跨域协作必须优先新增 `money-app-api` 中的场景契约。该规则不以共享 Entity 导入数量作为阻断条件，避免误伤归属 Feature 内部实现。

## Maven 物理拆分复核

当前不满足重新拆分 Maven 的条件：库存写侧和上述兼容调用仍会形成编译依赖；单体事务也仍跨订单、库存与会员资产。先完成 P1.4c 及其余 P1.6 场景契约、保持全量测试稳定，再独立评估循环依赖、模块独立编译收益和 Spring 装配。

## 下一最小任务

**P1.6.2：设计并实施 GMS 库存写命令契约，迁移 `GoodsStockFacade` 与 `PosInventoryActionService` 的 GMS 持久化实现。**
