# AD-2.3 共享 Entity 跨域新增引用门禁设计

## 目标与非目标

本设计为 `scripts/architecture-scan.sh --check-new` 增加一条 **additions-only** 规则：阻止 Feature
新增对其他所有者共享 `com.money.entity` Entity 的直接 Java import。它不把仍在共享包的所有 Entity
导入一律判错，也不阻断所有者在自身持久化实现中使用尚未物理迁移的 Entity。

本设计不移动 Entity、不改变 HTTP、DTO、表/Flyway、Mapper 扫描或事务。AD-2.3.1 才能实现脚本与
基线文件；实现前不得将报告中的 74 个导入文件直接变成失败数量。

## 适用范围与判定单位

扫描范围保持为：

```text
money-pos/qk-money-app/money-app-biz/src/main/java/com/money/feature/**
```

门禁的正常判定单位为下列四元组，而不是“命中文件数”：

```text
来源 Feature -> Entity 所有者 : 相对源码路径 : Entity 简单名 : import 形式
```

`来源 Feature` 取 `com/money/feature/<feature>/` 的第一级目录；不在 Feature 范围内的旧兼容包与
API 模块不纳入本规则。`import` 形式只能是 `single`（显式单类 import）或 `wildcard`
（`com.money.entity.*`）。同一文件显式导入多个 Entity 时，每个 Entity 是一个独立判定项。

## 所有权登记表

AD-2.3.1 应在脚本旁以受版本控制的、机器可读的 TSV/CSV 登记表保存下列映射；脚本不得从 Entity
类名前缀临时猜测所有者。`OmsRefundIdempotent` 已移出共享包，因此不在表内。

| 所有者 | 共享 Entity |
| --- | --- |
| GMS | `GmsBrand`, `GmsGoods`, `GmsGoodsCategory`, `GmsGoodsCombo`, `GmsInventoryDoc`, `GmsInventoryDocItem`, `GmsInventoryOrder`, `GmsInventoryOrderDetail`, `GmsStockLog`, `GmsTurnoverWarningSnapshot`, `PosSkuLevelPrice` |
| UMS | `GmsMemberTransaction`, `PosCouponRule`, `PosMemberCoupon`, `PosMemberLevel`, `UmsMember`, `UmsMemberBrandLevel`, `UmsMemberLog`, `UmsRechargeOrder` |
| TRADE | `OmsOrder`, `OmsOrderDetail`, `OmsOrderLog`, `OmsOrderPay` |
| HOME | `OmsDailySummary` |
| SYS | `Provinces`, `SysBrandConfig`, `SysPrintConfig`, `SysStrategy` |

未登记的 `com.money.entity` 单类 import 必须使 `--check-new` 失败：这防止新增 Entity 在未审查所有者前
被静默当作合法本域依赖。新增或迁移 Entity 时，必须在同一个经审查切片中更新所有权登记和相应基线，
不能靠前缀推断。

## 规则与失败语义

| 情形 | `--check-new` 行为 | 原因 |
| --- | --- | --- |
| 显式导入，来源 Feature 等于登记所有者 | 通过 | 所有者内部 ORM 使用合法，不需要基线豁免。 |
| 显式导入，来源 Feature 不等于登记所有者，且四元组在跨域基线 | 通过并报告为既有兼容桥/遗留债务 | 不扩大当前债务，但不误伤既有功能。 |
| 显式导入，来源 Feature 不等于登记所有者，且不在基线 | 失败 | 新增跨域 Entity 契约；应改用所有者 DTO、查询快照或命令端口。 |
| 未登记的显式 Entity | 失败 | 所有权尚未审查。 |
| 新增共享 Entity 通配符 import | 失败 | 通配符不能建立 Entity 级所有权，必须拆成显式 import 或先在专门切片登记。 |
| 既有通配符文件新增实际使用的非所有者 Entity | 失败 | 以类型级基线防止既有 `*` 成为绕过通道。 |

脚本在失败时至少输出来源 Feature、目标所有者、Entity、相对路径、import 形式以及修复方向：
“使用所有者提供的 Entity-free 契约；若这是既有兼容桥，先完成审计并在单独评审提交中更新基线”。
删除基线中的导入永远不失败；下一次基线审查应同步删掉无用豁免，保持债务清单可读。

## 现有跨域基线

下面是 AD-2.3 设计时的完整非所有者实际消费面。AD-2.3.1 的基线应逐项保存，不能按总数、Feature
对或 Entity 名称泛化放行。

| 来源 → 所有者 | 路径 | Entity | 形式 | 分类 |
| --- | --- | --- | --- | --- |
| GMS → SYS | `com/money/feature/gms/application/turnover/GmsTurnoverServiceImpl.java` | `SysStrategy` | single | 已记录的策略查询兼容面；后续改窄策略查询。 |
| GMS → SYS | `com/money/feature/gms/application/product/GmsGoodsExcelManager.java` | `SysBrandConfig` | wildcard | 既有 Excel 品牌配置兼容面。 |
| TRADE → UMS | `com/money/feature/trade/application/coupon/CouponRuleManagementService.java` | `PosCouponRule` | single | 优惠券管理持久化兼容面。 |
| TRADE → UMS | `com/money/feature/trade/application/coupon/CouponRuleManagementServiceImpl.java` | `PosCouponRule` | single | 同上。 |
| TRADE → UMS | `com/money/feature/trade/application/coupon/CouponRuleManagementServiceImpl.java` | `PosMemberCoupon` | single | 优惠券管理持久化兼容面。 |
| TRADE → UMS | `com/money/feature/trade/application/coupon/MemberCouponCountQueryService.java` | `PosMemberCoupon` | single | 既有计数查询实现兼容面。 |

`OmsDailySummary` 虽沿用 Oms 前缀，但登记所有者为 HOME；三个 HOME 导入均为本域合法使用，绝不能误报为
HOME → TRADE。此项是登记表必须优先于名称前缀的例证。

## 通配符的保守处理

当前共有三个既有 `import com.money.entity.*`：

| 路径 | 当前实际 Entity 使用 | 处理 |
| --- | --- | --- |
| `gms/application/product/GmsGoodsExcelManager.java` | `GmsBrand`, `GmsGoods`, `GmsGoodsCategory`, `PosSkuLevelPrice`, `SysBrandConfig` | 路径本身列入通配符基线；其中 GMS → SYS 的 `SysBrandConfig` 单独列入跨域类型基线。 |
| `trade/application/orderquery/OmsOrderServiceImpl.java` | `OmsOrder`, `OmsOrderDetail`, `OmsOrderLog`, `OmsOrderPay` | 路径列入通配符基线；均为 TRADE 本域。 |
| `trade/application/refund/OmsOrderRefundServiceImpl.java` | `OmsOrder`, `OmsOrderDetail`, `OmsOrderLog` | 路径列入通配符基线；均为 TRADE 本域。 |

实现时，脚本应读取所有权表的简单类名，在通配符文件中对每个类名作 Java 标识符边界匹配，生成实际类型
使用集合，再按上表规则检查非所有者集合相对基线的新增项。该匹配是保守的静态近似：注释或字符串出现
类名可能造成假阳性，但不会漏掉新增的显式类型名；遇到假阳性应拆分为显式 import，而不是放宽基线。
新通配符一律失败，即使当前只使用本域类型，因为它会让后续新增类型在 diff 中不再表现为 import 变更。

## 建议的 AD-2.3.1 文件与验收

实施只限于：

1. 更新 `scripts/architecture-scan.sh`，在现有 Shared Entity 报告之后增加本规则；默认 report 模式
   只展示分类，`--check-new` 才失败。
2. 新增版本控制的所有权与基线数据文件（建议置于 `scripts/architecture-baseline/`），分别保存 Entity
   所有权、显式跨域四元组、既有通配符路径及其跨域实际类型基线。
3. 更新架构扫描基线说明、接力说明、债务清单与实施台账；不修改业务源码。
4. 增加脚本级临时夹具/自检，至少覆盖：本域显式通过、新非所有者显式失败、既有桥通过、新通配符失败、
   既有通配符新增跨域实际类型失败、未登记 Entity 失败。

验收命令至少包含：

```bash
scripts/architecture-scan.sh
scripts/architecture-scan.sh --check-new
cd money-pos && mvn -q package -DskipTests
cd .. && git diff --check
```

AD-2.3.1 不需要为了脚本修改重跑业务集成数据库测试；如果触及 Maven 源码或测试夹具，才按接力说明使用
`money_pos_test` 跑隔离全量测试。

## 风险与升级条件

- 该门禁只覆盖 Java Feature 源码的 import，不能证明反射、XML FQCN、API 返回值或 `IService<Entity>`
  泛型不泄露；这些仍需实体迁移/契约切片逐项审计。
- 基线是临时兼容债务，不是长期白名单。每次移除桥或物理迁移 Entity，应在同一个提交同步收缩基线。
- 所有权登记是审查工件。出现新 Entity、共享 Entity 改所有者、或发现错误归属时，必须先更新本设计所述
  登记和证据，再允许实现。

## 下一步

唯一下一最小任务为 **AD-2.3.1：Entity 跨域 additions-only 门禁实施**。按本设计实现数据化所有权、
精确桥基线和通配符保守检测；不得扩大为全量共享 Entity 失败门禁。
