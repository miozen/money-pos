# MoneyPOS 阶段 4 控制器迁移收口报告：剩余 3 项

本报告记录 UMS 会员排行榜迁移后的当前快照，不修改 v1 初始基线。

## 本次切片

- `UmsMemberController` 的累计消费、余额、频次 Top 50 路由已改为调用 `UmsMemberService`。
- 服务保留三个原有 Mapper 查询与 `MemberRankVO` 字段；Controller 不再直接导入 `UmsMemberMapper`。
- 路由、`umsMember:list` 权限标记和前端 API 调用均不变。

## 验证与扫描

| 项目 | 结果 |
| --- | --- |
| UMS 排行榜集成测试 | `UmsMemberControllerRankIntegrationTest` 通过，验证消费额、余额、频次。 |
| 全量 `mvn test` | 13 个测试类、27 个用例通过；0 failures / 0 errors。 |
| `mvn package -DskipTests` | 通过。 |
| additions-only 门禁 | 通过。 |
| Controller 直接导入 Mapper | 3（初始基线 7）。 |
| 跨 Feature `ServiceImpl` / Mapper 导入 | 0。 |
| `platform → feature` 导入 | 0。 |
| Feature 导入共享 `com.money.entity` | 65；继续仅跟踪。 |

## 剩余项与下一步

剩余 Controller-Mapper 文件为 `GmsBrandConfigController`、`GmsGoodsExcelController` 和 `UmsMemberImportController`。下一最小任务是设计 `GmsBrandConfigController` 所需的 GMS→SYS 品牌配置窄服务契约，明确读取默认值和按品牌保存的兼容语义后再迁移；商品 Excel 与会员导入仍不在该切片内。
