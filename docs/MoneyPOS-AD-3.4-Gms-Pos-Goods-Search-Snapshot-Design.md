# AD-3.4 GMS→POS 商品搜索快照设计

## 目标与范围

为既有公开路由 `GET /gms/goods/pos-search?keyword=...` 设计一个由 GMS 实现、仅暴露
普通 DTO 的只读查询契约。下一实现切片将用它替换 `GoodsPosFacade` 对
`GmsGoodsService.lambdaQuery()`、`GmsGoods` 的直接依赖；路由、响应 DTO、数据库表、事务边界
都不改变。

本任务只固定 GMS 商品主档和等级价格矩阵的输入。`SysBrandConfigMapper` / `SysBrandConfig`
读取的品牌券策略仍由 Facade 在后续独立 SYS 策略边界任务处理，不能借本次商品快照迁移改变其
“策略未开启时券额清零”的公式。

## 当前调用面与所有权

| 位置 | 当前职责 / 输入 | 所有者问题 |
| --- | --- | --- |
| `PosGoodsController` | 保持 `/gms/goods/pos-search`，返回 `List<GmsGoodsVO>` | 兼容 REST 入口，不拥有商品持久化。 |
| `GoodsPosFacade` | 搜索商品、查询等级价，并结合品牌券策略组装 `GmsGoodsVO` | 直接注入 `GmsGoodsService` 并取得 `GmsGoods`，泄露 GMS 的通用 `IService<Entity>` 面。 |
| GMS 商品 / 价格域 | `GmsGoodsMapper`、`GmsGoodsPriceService.getPriceMap()`、`PosSkuLevelPrice` | 应在 GMS 内完成主档和等级价查询、再输出快照。 |
| SYS 品牌策略 | `SysBrandConfigMapper` 与 `couponEnabled` | 不属于本契约；本轮不移动也不改口径。 |

前端仓库只保留 `goodsApi.posSearch()` 的 API 封装，未发现对该路由返回字段的直接页面消费。
它仍是对外兼容接口，不能据此缩减或重命名响应。

## 已有契约不能复用的原因

已有 `PosGoodsCatalogQuery.searchForPos()` 是 TRADE `PosService.listGoods()` 的商品目录查询，
其快照字段仅满足 `PosGoodsVO`。它与本路由看似相近，但口径不同：

| 维度 | 既有 `PosGoodsCatalogQuery` | 本路由兼容快照 |
| --- | --- | --- |
| 返回目标 | `PosGoodsVO` | `GmsGoodsVO` |
| 主档字段 | POS 收银所需的最小字段 | 必须覆盖旧 `BeanUtil.copyProperties(GmsGoods, GmsGoodsVO)` 实际填充的字段。 |
| 搜索条件 | 空关键字不加过滤；助记码使用 `keyword.toUpperCase()`；不加状态条件 | 必须保持旧 Facade 实际生成的条件及大小写。 |
| 品牌券策略 | 不处理 | 仍由 Facade 根据 SYS 策略处理。 |

因此不能给既有契约追加“仅在旧接口才生效”的状态、字段或券策略语义；那会使两个公开 POS
路径共享一个含混口径。新契约应是并列的、面向兼容路由的 GMS 查询端口。

## 兼容口径（以现有可执行代码为准）

旧 Facade 的 MyBatis-Plus 链式表达式为：

```java
like(barcode, keyword).or().like(name, keyword).or().like(mnemonicCode, keyword).eq(status, "SALE")
```

未使用分组 wrapper。按 SQL 运算优先级，这等价于：

```text
barcode LIKE keyword OR name LIKE keyword OR (mnemonic_code LIKE keyword AND status = 'SALE')
```

而不是注释所称的“所有命中都必须 SALE”。这意味着以条码或名称命中时，非 `SALE` 商品也可能
返回。AD-3.4.1 必须以该实际行为为回归基线：不得趁实体解耦把它修正为
`(barcode OR name OR mnemonic_code) AND status = 'SALE'`。若要修正，须另立产品行为任务并
添加明确的接口回归。

其他固定口径：

- 对三个字段都执行包含匹配；关键字不做 `trim`、空值归一化或大写转换。空字符串仍保持旧的
  `LIKE '%%'` 行为。
- `mnemonicCode` 使用传入原样关键字；不得沿用 `PosGoodsCatalogQuery` 的大写转换。
- GMS 仍通过其价格服务按商品 ID 批量取得等级价格；每个等级输出 `memberPrice`。快照中的
  `memberCoupon` 保留原值或 `BigDecimal.ZERO`，不在 GMS 内判断品牌策略。
- 等级价格缺失时输出空 map；商品顺序保持 mapper 当前未指定排序的返回顺序。

## 建议 API 契约

在 `money-app-api` 新增 GMS 所有者中立查询端口（名称在实现前可按同一语义微调）：

```java
public interface LegacyPosGoodsSearchQuery {
    List<LegacyPosGoodsSearchSnapshot> searchForLegacyPos(String keyword);
}
```

`LegacyPosGoodsSearchSnapshot` 为 Java 8 普通不可变类：全部字段 `final`、完整构造函数、getter；
价格 map 在构造时复制并以不可修改 map 暴露。它只携带旧 `BeanUtil` 实际复制至
`GmsGoodsVO` 的商品字段及价格矩阵：

```text
id, brandId, categoryId, barcode, name, pinyin, pic, unit, size, description,
purchasePrice, salePrice, vipPrice, coupon, stock, sales, status,
createTime, updateTime, isDiscountParticipable, levelPrices, levelCoupons
```

`label`、`comboDesc`、`subGoodsList` 并非旧 Facade 从实体得到或自行装配的值，兼容适配层仍按
旧行为保留为 `null`。`isCombo` 同样不属于当前 `GmsGoodsVO`，不得因新快照额外向这个响应
引入字段。

## AD-3.4.1 实施边界与验收

1. 在 API 模块加入上述 Entity-free 契约和不可变快照；GMS 的 package-private Spring 实现只依赖
   自己的 mapper / 价格服务并完整复刻本文件的条件。
2. `GoodsPosFacade` 仅注入新查询端口，将快照逐字段映射回既有 `GmsGoodsVO`；删除
   `GmsGoodsService`、`GmsGoods` 及其 `lambdaQuery()` 使用。品牌策略及严格券额清洗保持原处。
3. 增加集成回归：条码/名称命中的非 `SALE`、助记码命中的非 `SALE`、原样小写助记码、空关键字、
   价格矩阵和“策略关闭则券额为零”。前两项用于锁定上述非分组 SQL 的兼容事实。
4. 验收 source search：`GoodsPosFacade` 不再 import `GmsGoodsService` 或 `GmsGoods`，且不调用
   `lambdaQuery()`；再运行专项、全量测试、打包、架构扫描与 `git diff --check`。

不在 AD-3.4.1 中迁移 `SysBrandConfigMapper`，不调整现有 `PosGoodsCatalogQuery`，不修改 HTTP 路由、
页面字段、表结构或 Flyway。
