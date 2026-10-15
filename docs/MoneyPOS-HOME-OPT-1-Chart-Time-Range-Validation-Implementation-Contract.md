# HOME-OPT-1：图表时间范围校验实施契约

`GET /home/charts` 已固定支持 `today`、`month`、`year`、`total` 四种时间范围。非法值目前会穿过
`HomeServiceImpl` 并在趋势补全时造成空时间范围异常。

本切片只在 HOME 服务边界拒绝不在该集合内的值，并使用项目既有 `BaseException` 失败语义。它同时保护 HTTP 调用与
未来的内部服务调用；不改变 GET 路径、缺省值 `today`、四个合法值的响应字段/趋势粒度/金额公式、快照调度、权限、
迁移或跨 Feature 契约。

验收：既有 HOME 集成回归继续覆盖四个合法范围及快照读写分离；新增测试证明非法范围在任何查询或写入前以明确业务
错误失败。验证按隔离 `money_pos_test` 协议运行 HOME 专项、全量 Maven 回归、打包、两项架构门禁与差异空白检查。
