# MoneyPOS 阶段 4 剩余 Controller-Mapper 盘点

本文件记录阶段 4.6.2 的只读盘点结果。目标是按依赖和行为边界选择下一个迁移切片，不在本次盘点中改变业务代码。

| Controller | 直连 Mapper | 路由与行为 | 风险判断 | 处理顺序 |
| --- | --- | --- | --- | --- |
| `UmsMemberController` | `UmsMemberMapper` | 仅三个只读 Top 50 排行：累计消费、余额、频次。其余会员档案路由已走 `UmsMemberService`。 | 低：同域单 Mapper、无写入、返回既有 `MemberRankVO`，服务接口已是该控制器的主边界。 | **下一切片**：把三条排行榜查询加入 `UmsMemberService` 并删除 Controller Mapper 注入。 |
| `GmsBrandConfigController` | `SysBrandConfigMapper` | 读取品牌优惠开关/等级，并按品牌更新或插入 SYS 配置。 | 中：只有两个路由和一个 Mapper，但 Entity/Mapper 逻辑归 SYS，需先定义 GMS 使用 SYS 配置的窄服务契约。 | 排行榜切片之后。 |
| `GmsGoodsExcelController` | `GmsGoodsCategoryMapper`、`GmsBrandMapper`、`SysDictDetailMapper` | 商品导入、动态模板、全量导出；还组合价格矩阵和商品服务。 | 高：Excel 输出、动态下拉、多张表、价格矩阵和 SYS 字典共同参与，不能只机械包一层 Service。 | 在 GMS 元数据/Excel 专用服务边界设计后处理。 |
| `UmsMemberImportController` | `GmsBrandMapper`、`SysDictDetailMapper`、`UmsMemberMapper`、`UmsMemberBrandLevelMapper`、`PosMemberCouponMapper` | 会员导入、批量发券、动态模板、全量会员资产/品牌等级导出。 | 高：跨 GMS/SYS 数据、会员资产和动态 Excel 批处理同时存在；需要专用导入/导出读模型。 | 最后处理，拆成模板与导出两个子切片。 |

## 选择依据

`UmsMemberController` 的三个排行榜路由是唯一满足以下条件的剩余项：只读、同 Feature、单 Mapper、既有应用服务已经承担同一控制器的其他路由、没有文件下载或跨 Feature 参考数据。迁移时只新增服务接口方法并保持 `/ums/member/rank/consume`、`/rank/balance`、`/rank/frequency` 与 `MemberRankVO` 输出不变。

`UmsMemberServiceImpl.MemberGoodsRankVO` 的实现类嵌套类型泄漏属于既有 P1/会员画像 DTO 债务；不与本次排行榜 Controller-Mapper 切片混合处理。
