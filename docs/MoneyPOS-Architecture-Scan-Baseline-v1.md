# MoneyPOS 架构扫描基线 v1

此基线由 `scripts/architecture-scan.sh` 产生规则语义，并在阶段 4.3 固定。扫描只覆盖 `money-app-biz/src/main/java`。默认运行仍是**非阻断报告**；阶段 4.5.2 起，显式 `--check-new` 会以本基线作为允许清单，仅对前三条规则的新增项返回失败，尚未接入 Maven 或 CI。

| 规则 | v1 结果 | 已知例外 / 处理方式 |
| --- | ---: | --- |
| Controller 直接导入 Mapper | 7 个文件 | `SysStrategyController`、`GmsBrandConfigController`、`GmsGoodsExcelController`、`GmsStockLogController`、`PosCouponRuleController`、`UmsMemberController`、`UmsMemberImportController`。仅可通过独立迁移切片减少，不得扩展。 |
| 跨 Feature 导入 `ServiceImpl` 或 Mapper | 0 | 当前无例外；任何新发现都应在代码审查中解释并优先改为接口/facade。 |
| `platform` 导入 `feature` | 0 | 当前无例外；这是未来最先可升级为新增违规门禁的规则。 |
| Feature 导入共享 `com.money.entity` | 62 个文件 | 详见 `MoneyPOS-Entity-Ownership-and-DTO-Plan.md`。尚未物理按 Feature 划分，故当前只跟踪，不能按数量变化自动判错。 |

## 使用方式

在仓库根目录运行：

```bash
bash scripts/architecture-scan.sh
```

默认脚本只输出 Markdown 报告，即使发现依赖也返回 0。需要本地门禁验证时运行：

```bash
bash scripts/architecture-scan.sh --check-new
```

该模式允许上表中的历史 Controller-Mapper 文件（即使后续迁移已减少），但新增 Controller-Mapper、跨 Feature `ServiceImpl`/Mapper 或 `platform → feature` 发现会以退出码 1 失败；共享 Entity 始终只报告。代码评审仍需把报告与本基线比较：

1. Controller-Mapper 数量增加，或出现新的文件，必须在合并前移除或获得明确、可追踪的临时豁免。
2. 跨 Feature `ServiceImpl`/Mapper、以及 `platform → feature` 的新增结果，不应接受为普通兼容项；应改为服务接口、facade 或中立契约。
3. 共享 Entity 数量变化必须结合归属表人工审查，避免把“本域内部实现”或既有迁移误报为跨域违规。

## 升级条件

连续两个迁移切片运行结果稳定、P0 优惠券 DTO 切片完成后，前三条规则已实现本地“仅拒绝新增项”门禁。先保持 Maven/CI 不接入，待一次独立的开发流程复核后再决定是否接入。共享 Entity 规则在 P1/P2 场景 DTO 完成前始终保持报告模式。
