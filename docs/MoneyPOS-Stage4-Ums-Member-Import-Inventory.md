# MoneyPOS 阶段 4 UMS 会员导入入口盘点与迁移切片

本文件对应阶段 **4.6.6a**。本次仅盘点 `/ums/member` 的 Excel 模板、资产导出、老会员导入和批量发券边界，并为后续迁移划分安全切片；不改变代码、会员资产、优惠券或前端。

## 路由与责任矩阵

| 路由 | 前端入口 | 当前职责 | 写入/依赖风险 | 处理决定 |
| --- | --- | --- | --- | --- |
| `POST /ums/member/import` | `memberApi.importMembers()`、会员工具栏 | Controller 已委托 `UmsMemberService.importMembers()`，再由事务型 `UmsMemberImportService` 解析动态品牌列、增改会员、合并品牌等级、写余额/券/满减券日志 | 高：会员资产、会员等级、满减券、日志、GMS 品牌与 SYS 等级字典共同参与 | 保持不动。 |
| `POST /ums/member/batch-issue-voucher` | 会员召回页面 | Controller 已委托 `UmsMemberService.batchIssueVoucher()` | 高：批量新增未使用满减券，已有事务 | 保持不动。 |
| `GET /ums/member/template` | `memberApi.downloadTemplate()`、会员工具栏 | Controller 组装固定 5 列与动态品牌特权列、会员等级下拉和示例行，写入工作簿 | 低：只读 GMS 品牌和 SYS 会员等级字典 | **首个安全切片**。 |
| `GET /ums/member/export` | 会员工具栏 Blob 下载 | Controller 组装同一表头，读取会员主档、品牌等级矩阵、未使用满减券数，再写入工作簿 | 中高：跨 GMS/SYS/TRADE 参考数据和 UMS 资产读模型 | 模板切片后，再先定义 TRADE 满减券读取契约。 |

## 当前直接 Mapper 与可替换边界

`UmsMemberImportController` 当前直接注入五个 Mapper：

| Mapper | 使用路由 | 所属边界 | 后续替代方式 |
| --- | --- | --- |
| `GmsBrandMapper` | 模板、导出 | GMS 品牌目录 | `GmsBrandService.getBrandSelect()` 的选择 DTO。 |
| `SysDictDetailMapper` | 模板、导出 | SYS 字典 | `SysDictDetailService.getValueToCnDescMap("memberType")` 的有序代码/中文名映射，排除 `MEMBER`。 |
| `UmsMemberMapper` | 导出 | UMS 本域会员主档 | UMS 读模型服务或现有 `UmsMemberService.list()`。 |
| `UmsMemberBrandLevelMapper` | 导出 | UMS 本域品牌等级矩阵 | UMS 导出读服务内部的同域持久化访问。 |
| `PosMemberCouponMapper` | 导出 | TRADE 优惠券资产 | 先建立 TRADE→UMS 的窄“未使用券计数”查询契约，不能新增 UMS→TRADE Mapper 依赖。 |

导入服务中也有同类 Mapper，但它们位于既有写入事务服务，不是本次 Controller→Mapper 清理对象；将其与只读输出重构混合会扩大数据风险。

## 选择的安全迁移顺序

### 4.6.6b：会员 Excel 模板服务（下一最小任务）

新增 `UmsMemberExcelTemplateService`，仅承接 `GET /ums/member/template`：

```text
UmsMemberImportController → UmsMemberExcelTemplateService
  ├─ GmsBrandService
  └─ SysDictDetailService
```

服务须统一组装固定五列与 `[品牌特权] {品牌名}` 动态列、动态等级下拉和示例行，并保持工作表 `会员数据填写区`、下载响应头及 `智能会员导入模板.xlsx` 不变。它经品牌选择 DTO 和 SYS 有序字典映射读取数据，不导入跨域持久化 Entity；同时向下一切片公开只读的表头元数据，确保导出继续与模板严格对齐。

这一步只消除 Controller 对 GMS/SYS 两个 Mapper 的使用；导出仍需要 UMS/TRADE 读模型，因此 Controller 暂时仍是一个扫描发现。这样做的价值是先固化可复用的表头契约，而不是复制两套品牌列逻辑。

### 4.6.6c：会员资产导出读模型（后续，须先设计）

在模板服务稳定后，迁移 `GET /ums/member/export` 至 UMS 只读导出服务。该服务可使用 UMS 本域会员与品牌等级读取，但优惠券统计必须通过 TRADE 提供的窄查询接口，例如：

```java
Map<Long, Long> countUnusedCouponsByMemberIds(Collection<Long> memberIds);
```

该接口只返回会员 ID 到未使用券数量的聚合结果，不暴露 `PosMemberCoupon` Entity 或 Mapper。导出服务经 `GmsBrandService`、`SysDictDetailService` 和该 TRADE 查询接口取得参考数据；完成后 Controller 才能彻底移除剩余 Mapper，扫描预期由 1 降为 0。

## 必须保持的兼容语义

| 范围 | 要求 |
| --- | --- |
| 模板 | 固定五列为姓名、手机号、初始余额、初始会员券、初始满减券；后续动态品牌列的顺序与当前品牌读取顺序一致。 |
| 下拉与示例 | 每个动态品牌列使用排除 `MEMBER` 后的 `memberType.cnDesc` 下拉；示例行保留“张老板 / 13800138000 / 500.00 / 50.00 / 2”。 |
| 导出 | 表头必须与模板一致；仅统计 `UNUSED` 满减券；品牌等级 code 必须译回 `cnDesc`；无会员仍返回空表。 |
| HTTP 与权限 | 四条既有路由、HTTP 方法、`file` 上传字段、四个 `@PreAuthorize` 语义、Blob 下载与响应文件名保持不变。 |
| 写入路径 | 导入的资产叠加、逻辑删除恢复、品牌等级合并、导入日志、默认券规则 ID，以及批量发券事务均不改变。 |

## 验收计划

4.6.6b 应新增隔离数据库工作簿测试，验证动态品牌表头、会员等级下拉、示例行和响应头。4.6.6c 再增加真实会员、品牌等级、未使用/已使用券和空会员导出的读模型测试。每个实现切片均运行 `mvn test`、`mvn package -DskipTests` 与 `bash scripts/architecture-scan.sh --check-new`。

本盘点不迁移写入型 `UmsMemberImportService`，不修改优惠券表、会员表、品牌等级表或前端，也不将 TRADE 优惠券 Mapper 暴露给 UMS。
