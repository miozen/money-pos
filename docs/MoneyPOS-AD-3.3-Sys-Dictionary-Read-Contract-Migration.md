# AD-3.3 SYS 字典值/描述查询契约迁移

## 目标

消除业务 Feature 对 `SysDictDetail` 持久化实体及 `SysDictDetailMapper` 的读取依赖，统一使用
SYS 已提供的 `SysDictDetailService.getValueToCnDescMap(dict)`。SYS 自己的管理端仍保留实体读写。

## 迁移范围

- TRADE：支付渠道名称、订单状态翻译、POS 会员等级显示；保留大小写无关匹配、`orderStatus`
  到 `order_status` 的回退以及枚举兜底。
- GMS：商品 Excel 模板/导出会员特价列与导入的中文等级反向映射；保留字典排序、排除 `MEMBER`
  和价格列按等级 code 对齐的语义。
- UMS：会员档案等级显示与 Excel 导入的中文等级反向映射。

`UmsMemberExcelTemplateService` 在本轮前已使用该映射契约，未做无效修改。

## 验收

生产 Feature 源码中不再存在 `listByDict()`、`SysDictDetail` 或 `SysDictDetailMapper` 的直接使用。
GMS Excel、GMS 导入、UMS 导入/模板以及 TRADE 结算专项均验证通过。未改动 SYS 管理接口、字典表、
Flyway、HTTP 路由或事务边界。
