# MoneyPOS 阶段 4 控制器迁移收口报告：剩余 2 项

本报告记录品牌配置迁移后的当前快照，不修改 v1 初始基线。

## 本次切片

- `GmsBrandConfigController` 已改为调用 `GmsBrandPricingConfigService`；该服务通过 `SysBrandConfigService` 访问 SYS 配置持久化边界。
- Controller 不再导入 `SysBrandConfig` 或 `SysBrandConfigMapper`。
- `GET`/`POST /gms/brand/config`、请求 JSON 字段、默认值和按品牌保存语义均保持不变。

## 验证与扫描

| 项目 | 结果 |
| --- | --- |
| 品牌配置集成测试 | `GmsBrandConfigControllerIntegrationTest` 通过，覆盖默认、插入、更新、空等级。 |
| 全量 `mvn test` | 14 个测试类、28 个用例通过；0 failures / 0 errors。 |
| `mvn package -DskipTests` | 通过。 |
| additions-only 门禁 | 通过。 |
| Controller 直接导入 Mapper | 2（初始基线 7）。 |
| 跨 Feature `ServiceImpl` / Mapper 导入 | 0。 |
| `platform → feature` 导入 | 0。 |
| Feature 导入共享 `com.money.entity` | 64；继续仅跟踪。 |

## 剩余项与下一步

仅剩 `GmsGoodsExcelController` 与 `UmsMemberImportController`。两者都包含动态模板、Excel 输出及多表批处理，不能作为单一机械包装迁移。下一最小任务是先盘点 GMS 商品 Excel 的模板、导出和导入调用面，识别可独立迁移的子切片；会员导入继续保持后续处理。
