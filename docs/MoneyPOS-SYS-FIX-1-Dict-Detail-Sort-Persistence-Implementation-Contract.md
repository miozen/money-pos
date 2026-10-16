# SYS-FIX-1：字典详情排序持久化实施契约

## 已确认问题

字典详情页面提交 `sort`，数据表和查询也分别保存并按 `sort ASC` 读取；但 `SysDictDetailDTO` 缺少该字段。
`BeanUtil.copyProperties` 因而不会把用户填写的排序值传入 `SysDictDetail`，新增记录始终使用数据库默认值 `999`，修改记录也无法变更排序。

## 最小变更

仅为 `SysDictDetailDTO` 增加 `Integer sort`。现有 `SysDictDetailServiceImpl` 的对象复制、`sort ASC` 查询、路由、权限、
响应、数据库默认值和历史字典数据均不改变。

## 验收

新增或扩展隔离数据库集成回归，证明新增详情持久化请求排序值、修改可改变排序值，且 `listByDict` 按升序返回。
再执行受影响测试、隔离 `money_pos_test` 全量 Maven 回归、打包、架构门禁和差异空白检查。
