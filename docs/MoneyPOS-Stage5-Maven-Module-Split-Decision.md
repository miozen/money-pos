# 阶段 5.3：Maven 模块拆分决策

## 决策

**决定：暂不进行物理 Maven 模块拆分。**

这不是放弃模块化，而是基于现有证据拒绝一次没有独立构建收益、且会引入循环依赖或事务回归风险的 POM/源码移动。阶段 5 至此没有授权创建 `money-app-gms`、`money-app-ums`、`money-app-trade` 或 `money-app-contract` 的目录与 POM。

当前继续维持：`money-app-api`、`money-app-system`、`money-app-biz` 三个应用 reactor 模块；GMS、UMS、TRADE 继续作为 `money-app-biz` 内已受阶段 4 门禁保护的逻辑 Feature。

## 当前图与目标图

### 当前

```text
money-app-api  ─┐
money-app-system├──> money-app-biz
                │      ├─ feature.gms
                │      ├─ feature.ums ─────┐
                │      └─ feature.trade ───┼─ direct two-way calls
                │                           │
                └──────────────────────────┘
```

`feature.ums → feature.trade` 的优惠券数量查询，与 `feature.trade → feature.ums` 的会员读取、POS 聚合、结算和退款协调共同构成循环；TRADE 还直接持有 UMS Entity/Mapper/IService 使用点。

### 允许在未来实施的目标

```text
money-app-api       money-app-system        money-app-contract
      │                    │                       ▲
      │                    ├──────────┐            │
      ▼                    ▼          │            │
money-app-gms   money-app-ums   money-app-trade ───┘
      ▲               ▲                  ▲
      └───────────────┴──────────────────┘
                 money-app-boot
```

约束如下：

- `money-app-contract` 不依赖任何业务 Feature；只保存 Entity-free DTO 和 port 接口。
- GMS 可单向依赖 API/SYS；UMS、TRADE 可消费 GMS 的稳定读契约。
- UMS 与 TRADE 都可依赖 contract，但彼此不得有 Maven 依赖。
- `money-app-boot` 是最终 Spring Boot 组合层，不得成为新的业务逻辑所有者。

## 准入矩阵

| 准入条件 | 当前证据 | 结论 |
| --- | --- | --- |
| 稳定跨域契约 | 阶段 4 已消除 Controller→Mapper 与跨 Feature 实现/Mapper；但 UMS/TRADE 仍互用 Entity、Mapper、`IService<Entity>` | 不通过 |
| 无 Maven 循环 | UMS↔TRADE 有双向源码依赖 | 不通过 |
| 独立构建收益 | GMS 有 36 个源文件和 4 个测试，但测试仍启动完整应用；只有 `money-app-biz` 可独立选择 | 不通过 |
| 运行时可装配 | 当前全应用测试通过，但尚未证明拆分后的 Mapper、Bean、Flyway 和事务装配 | 不通过 |
| GMS 候选方向 | GMS 不直接依赖 UMS/TRADE，可被外部单向消费 | 方向通过，实施仍不通过 |

因此不存在能够安全提交 POM/源码移动计划的 Feature 候选。

## 再次评估的最小前置条件

只有以下工作实际完成并验证后，才重新打开物理拆分评估：

1. [x] P1.1 已将 Entity-free 的券数量查询下沉为 API 中立契约 `MemberCouponCountQuery`，由 TRADE 实现；UMS 不再 import TRADE 源码包。
2. [ ] 用会员快照、POS 会员查询和订单档案 DTO 替代 TRADE 对 `UmsMemberService`、`UmsMember`、`UmsMemberBrandLevelMapper` 的只读依赖。
3. [ ] 用会员结算/退款命令替代 TRADE 对 `UmsMemberLogMapper`、`lambdaUpdate` 和 UMS 资产服务的直接写入；结算、并发券核销与退款特征测试必须保持通过。
4. [ ] GMS 对外读取不再以 `IService<Entity>` 作为新增契约，并为候选模块建立不依赖完整 Boot 组合层的编译/测试证明。
5. [ ] 在不改变业务行为的前提下，先用依赖图和 `mvn -pl <candidate> -am test-compile` 验证一个候选模块；通过后才提出单独的 POM/源码移动任务。

## 维护规则

阶段 5 完成前后，所有正常开发仍继续运行：

```bash
bash scripts/architecture-scan.sh --check-new
```

这确保“暂不拆分”不会退化为重新允许架构违规。物理拆分是后续独立授权的演进任务，不是当前业务开发的前置条件。
