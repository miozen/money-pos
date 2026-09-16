# MoneyPOS 阶段 4.5 架构扫描报告

本报告是阶段 4.4 首个迁移切片后的当前快照。它与 `MoneyPOS-Architecture-Scan-Baseline-v1.md` 的初始 7 项基线并存，不覆盖历史基线。

| 检查项 | v1 初始值 | 当前值 | 结论 |
| --- | ---: | ---: | --- |
| Controller 直接导入 Mapper | 7 | 6 | `SysStrategyController` 已迁移为 Service 调用；没有新增违规。 |
| 跨 Feature `ServiceImpl` / Mapper 导入 | 0 | 0 | 保持为零。 |
| `platform → feature` 导入 | 0 | 0 | 保持为零。 |
| Feature 导入共享 `com.money.entity` | 62 个文件 | 62 个文件 | 仅跟踪；等待 P0/P1/P2 DTO 切片，不作为阻断项。 |

## 剩余 Controller-Mapper 项及可执行处理

| 文件 | 后续切片 | 暂不立即迁移的原因 |
| --- | --- | --- |
| `GmsStockLogController` | 下一优先：新增 GMS 库存流水查询服务，保持分页与筛选 SQL。 | 低耦合只读查询，适合作为第二个稳定样本。 |
| `GmsBrandConfigController` | GMS 品牌配置服务切片。 | 使用 SYS 配置 Entity/Mapper，须先明确 GMS 对 SYS 配置的窄查询/保存契约。 |
| `PosCouponRuleController` | P0 优惠券规则 DTO + UMS 权益服务切片。 | 目前直接暴露 `PosCouponRule`，不应只包一层 Service 而遗漏 DTO 契约问题。 |
| `GmsGoodsExcelController` | GMS 商品 Excel 边界切片。 | 同时读取品牌、分类、价格与字典，涉及多个共享 Mapper。 |
| `UmsMemberController` | UMS 会员档案/画像 DTO 切片。 | 除 Mapper 外还暴露 `UmsMemberServiceImpl.MemberGoodsRankVO`。 |
| `UmsMemberImportController` | UMS 导入服务切片。 | 同时依赖会员、品牌、券、等级和字典兼容层。 |

## 门禁结论

剩余项均有明确的后续切片，因此可作为可追踪的历史基线继续开发；但目前仅完成一个 Controller-Mapper 迁移，且 P0 优惠券 DTO 尚未完成。扫描继续保持报告模式，不接入 Maven 或 CI 阻断。

下一最小任务：迁移 `GmsStockLogController` 到 GMS 库存流水查询服务，完成第二个稳定样本后再评估 additions-only 门禁的准备度。
