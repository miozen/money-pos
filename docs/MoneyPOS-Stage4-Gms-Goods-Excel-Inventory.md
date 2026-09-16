# MoneyPOS 阶段 4 GMS 商品 Excel 盘点与迁移切片

本文件对应阶段 **4.6.5a**。本次只盘点 `/gms/goods` 的 Excel 模板、导出、导入三条链路，并定义下一实现切片；不改变业务代码、路由、数据库或前端。

## 调用面与当前职责

| 路由 | 前端入口 | 当前后端职责 | 数据/副作用 | 本次判断 |
| --- | --- | --- | --- | --- |
| `GET /gms/goods/template` | `useGoodsImportExport.js` 的“下载模板” | Controller 组装动态表头、分类/品牌/状态/满减下拉、示例行并写入工作簿 | 仅读分类、品牌、会员等级字典 | 迁入只读 Excel 服务。 |
| `GET /gms/goods/export` | `GoodsToolbar.vue` 的“导出” | Controller 组装同一动态表头，读取商品、分类、品牌和会员价格矩阵，再写入工作簿 | 仅读商品、价格、分类、品牌、会员等级字典 | 与模板一并迁入同一只读 Excel 服务。 |
| `POST /gms/goods/import` | `useGoodsImportExport.js` 的“导入” | Controller 已只委托 `GmsGoodsExcelManager.importGoods(file)` | 分类/品牌按需创建、商品新增或更新、库存/会员价矩阵写入，且受事务保护 | **保持不动**，不混入只读迁移。 |

Controller 当前直接使用 `GmsGoodsCategoryMapper`、`GmsBrandMapper`、`SysDictDetailMapper`。模板和导出共用 `buildDynamicHeads()`，其动态列来自排除基础会员 `MEMBER` 后的 `memberType` 字典；若分开改写，会员价格列的顺序或名称会发生漂移，导入模板和导出文件将不再兼容。

## 已确认的可复用服务边界

- `GmsGoodsCategoryService`、`GmsBrandService` 可提供分类和品牌的读取，不再需要 Controller 注入 Mapper。
- `SysDictDetailService.listByDict("memberType")` 已是 SYS 提供的读取接口；在 GMS 服务中按现有语义排除 `MEMBER` 即可，不再需要 Controller 注入 `SysDictDetailMapper`。
- `GmsGoodsService.list()` 与 `GmsGoodsPriceService.getPriceMap(goodsIds)` 已是商品档案和批量等级价格读取边界。
- `GmsGoodsExcelManager` 已承担写入型导入及其事务，不能在本次迁移中拆解其动态表头解析、品牌配置、商品/库存/价格更新行为。

现有 `SysDictDetailService` 仍返回兼容 Entity，这是阶段 4 P1 的跨域 DTO 债务；本切片只消除 Controller→Mapper 依赖，不扩展为 SYS 字典契约重构。

## 选择的安全迁移子切片：只读 Excel 生成

新增 GMS 应用服务（建议命名 `GmsGoodsExcelReadService`），由它统一承担：

```text
GmsGoodsExcelController
  ├─ GmsGoodsExcelManager.importGoods(file)        # 原样保留，写入/事务
  └─ GmsGoodsExcelReadService
       ├─ writeTemplate(response)
       └─ writeExport(response)
```

不将模板单独迁移。模板和导出作为一个**只读**子切片同时迁移的原因是二者必须使用同一动态表头；合并后 Controller 可立即移除全部三个 Mapper 注入，而不引入重复逻辑。导入路由仍只委托现有 Manager，因而整个 Controller 迁移后也不保留 Mapper。

### 必须保持的行为

| 范围 | 兼容要求 |
| --- | --- |
| HTTP 与前端 | 三个既有 URL、HTTP 方法、上传字段 `file`、下载 Blob 行为和响应头均不变。 |
| 动态表头 | 固定 11 列（含“参与满减”）后，按现有 `memberType` 查询结果、排除 `MEMBER` 的顺序附加 `[会员特价] {cnDesc}`。模板与导出必须调用同一个表头组装方法。 |
| 模板 | 工作表名 `商品资料填写区`、分类/品牌下拉在第 2/3 列、状态和满减下拉在第 4/5 列、示例行与当前模板一致。 |
| 导出 | 工作表名 `全量商品数据`；分类/品牌 ID 转名称；上架状态转 `上架 (SALE)`，其他状态转 `下架 (SOLD_OUT)`；满减转 `允许`/`禁止`；等级价格按动态字典列填充；无商品时不查询价格矩阵。 |
| 文件输出 | 保持 MIME 类型、UTF-8、`Content-disposition` 编码以及 `智能商品导入模板.xlsx`、`门店商品全量档案.xlsx` 两个文件名。 |
| 导入 | `GmsGoodsExcelManager`、其事务边界、品牌/分类自动创建、商品增改、库存和会员价格矩阵写入都不改变。 |

## 后续实现与验收

1. 新增只读服务并将模板、导出两条路由委托给它；Controller 只保留该服务和现有 `GmsGoodsExcelManager`。
2. 为服务或 Controller 增加隔离数据库集成测试：模板工作簿的表头/示例行/下拉元数据，以及包含分类、品牌、满减和会员价的导出行；同时覆盖空商品导出。
3. 运行隔离 `money_pos_test` 的全量 `mvn test`、`mvn package -DskipTests` 和 `bash scripts/architecture-scan.sh --check-new`。预期 Controller→Mapper 扫描由 2 降至 1，仅剩 `UmsMemberImportController`。

本切片不更改前端，不重构写入型导入，不合并 `GmsGoodsExcelManager` 与 `GmsGoodsPriceService`，也不处理系统字典的 Entity→DTO P1 工作。
