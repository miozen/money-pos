# MoneyPOS 架构扫描基线 v1

此基线由 `scripts/architecture-scan.sh` 产生规则语义，并在阶段 4.3 固定。扫描只覆盖 `money-app-biz/src/main/java`。默认运行仍是**非阻断报告**；显式 `--check-new` 会以本基线作为允许清单，对结构规则及共享 Entity 的新增跨所有者使用返回失败，尚未接入 Maven 或 CI。

| 规则 | v1 结果 | 已知例外 / 处理方式 |
| --- | ---: | --- |
| Controller 直接导入 Mapper | 7 个文件 | `SysStrategyController`、`GmsBrandConfigController`、`GmsGoodsExcelController`、`GmsStockLogController`、`PosCouponRuleController`、`UmsMemberController`、`UmsMemberImportController`。仅可通过独立迁移切片减少，不得扩展。 |
| 跨 Feature 导入 `ServiceImpl` 或 Mapper | 0 | 当前无例外；任何新发现都应在代码审查中解释并优先改为接口/facade。 |
| `platform` 导入 `feature` | 0 | 当前无例外；这是未来最先可升级为新增违规门禁的规则。 |
| Feature 导入共享 `com.money.entity` | 73 个文件 | 默认仍只报告总量；`scripts/architecture-baseline/shared-entity-owners.tsv` 记录 27 个 Entity 所有者，`shared-entity-cross-domain-baseline.tsv` 精确登记 6 条既有跨域桥，`shared-entity-wildcard-baseline.tsv` 记录 3 个既有通配符路径。 |

## 使用方式

在仓库根目录运行：

```bash
bash scripts/architecture-scan.sh
```

默认脚本只输出 Markdown 报告，即使发现依赖也返回 0。需要本地门禁验证时运行：

```bash
bash scripts/architecture-scan.sh --check-new
```

该模式允许上表中的历史 Controller-Mapper 文件（即使后续迁移已减少），但新增 Controller-Mapper、跨 Feature `ServiceImpl`/Mapper、`platform → feature`，以及新增或未登记的共享 Entity 跨所有者使用会以退出码 1 失败。代码评审仍需把报告与本基线比较：

1. Controller-Mapper 数量增加，或出现新的文件，必须在合并前移除或获得明确、可追踪的临时豁免。
2. 跨 Feature `ServiceImpl`/Mapper、以及 `platform → feature` 的新增结果，不应接受为普通兼容项；应改为服务接口、facade 或中立契约。
3. 共享 Entity 数量变化必须结合归属表人工审查，避免把“本域内部实现”或既有迁移误报为跨域违规；新增非所有者显式 import、未登记 Entity 与新通配符必须移除或在独立审查切片中更新精确基线。

## 升级条件

连续两个迁移切片运行结果稳定、P0 优惠券 DTO 切片完成后，结构规则和共享 Entity 跨所有者规则已实现本地“仅拒绝新增项”门禁。先保持 Maven/CI 不接入，待一次独立的开发流程复核后再决定是否接入。共享 Entity 的文件总数继续保持报告模式。
