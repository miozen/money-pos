# 阶段 5.2：GMS 单向模块候选与契约最小集评估

## 结论

GMS 是 GMS、UMS、TRADE 三者中**依赖方向最适合作为首个候选**的 Feature：当前 GMS 源码不直接导入 UMS 或 TRADE；UMS、TRADE、HOME 和遗留兼容入口反向消费 GMS 服务。它因此不构成 Maven 循环的源头。

但 GMS 尚未通过物理拆分准入。本步不创建 `money-app-gms`，原因是 GMS 服务接口仍通过 `IService<GmsGoods/GmsBrand>` 暴露持久化 Entity，GMS 内部仍使用共享 API 中的 Entity/Mapper 和 SYS 配置/字典能力，现有四项 GMS 集成测试也都从完整 Spring Boot 应用启动，而非独立模块测试。单独移动 POM 只能制造新的编译边，不能证明独立构建价值。

## 依赖基线

| 项目 | 观察结果 | 对候选模块的含义 |
| --- | --- | --- |
| GMS 源码规模 | 36 个 Java 文件，包含目录、商品、库存、单据、周转、分析、Excel、REST 与本域 mapper | 一个候选模块必须整体保留这些层，不能只移动 Controller 或 Service。 |
| GMS→UMS / TRADE | 0 个直接 Feature import | 方向上可作为下游被消费的模块，不会与 UMS/TRADE 形成直接循环。 |
| 外部消费者 | TRADE 6 个、UMS 3 个、HOME 2 个，以及遗留 Controller/Service 2 个源码文件导入 GMS 服务 | 未来应是 `UMS/TRADE/HOME/boot → GMS`，消费者不能继续访问 GMS 内部 mapper 或 Entity。 |
| 共享持久化依赖 | 22 个文件导入共享 Entity、11 个文件导入 Mapper | 现阶段可经 `money-app-api` 编译，但尚不是独立持久化边界；需要与 P1 逐步收敛。 |
| SYS 引用 | 品牌配置服务用 `SysBrandConfigService`；Excel、周转仍有 SYS Entity/Mapper/字典使用 | 可保持单向 `GMS → money-app-system/API`，但先要把跨域 Mapper 进一步收口为 SYS 服务契约。 |
| 测试 | 4 项 GMS 集成测试，均以完整应用上下文运行 | 不能把“能跑全应用测试”视为 GMS 可独立测试；需先证明候选模块的 test-compile / 窄集成测试价值。 |

## 目标依赖形状（尚未实施）

```text
money-app-api       money-app-system       money-app-contract
       │                    │                     │
       └──────────────┬─────┘                     │
                      ▼                           │
                money-app-gms  ◄──────────────────┘
                      ▲
          ┌───────────┼────────────┐
          │           │            │
     money-app-ums  money-app-trade  money-app-boot
```

这里的 `money-app-gms` 只是评估名称，不是待立即创建的目录。它可以单向依赖 API/SYS/公共技术能力；UMS、TRADE 以及最终启动组合模块可依赖它。若某个跨域调用只需要商品或品牌资料，应优先定义 GMS 的只读快照契约，而不是让消费者继续获得 `IService<Entity>`。

## 中立契约模块的最小依赖集

为解决 5.1 的 UMS↔TRADE 循环，未来的 `money-app-contract` 首切片应严格保持最小：

| 可放入 | 不可放入 |
| --- | --- |
| Java 标准库类型、`BigDecimal`、集合；Entity-free DTO；`MemberCouponCountQuery`、会员核验/订单档案/资产命令等 port 接口 | Spring `@Service`、MyBatis Mapper、`IService`、`com.money.entity.*`、Feature `ServiceImpl`、Controller、数据库配置与 Flyway |

因此首个“未使用券数量”契约在技术上甚至不需要依赖 `money-app-api` 或 `money-app-system`；它只需 JDK 集合。契约模块必须由 UMS、TRADE、GMS 同级依赖，不能反向依赖任何业务模块。

## 独立编译收益判断

目前唯一可用的业务构建单元是 `money-app-biz`，现有 `mvn -pl qk-money-app/money-app-biz -am test-compile` 仍会编译整个业务 Feature 集合。若未来 GMS 候选成立，以下才是有价值的验证：

```bash
mvn -pl qk-money-app/money-app-gms -am test-compile
mvn -pl qk-money-app/money-app-gms -am test
```

预期收益是把 36 个 GMS 源文件及其窄测试与 UMS/TRADE 的变更隔离；但在测试仍依赖完整启动应用、跨域契约仍泄露 Entity 时，该收益尚未成立。故本步结论是**候选成立、实施未获批准**。

## 5.2 决策与下一步

不修改 POM，不移动 GMS 源码。先在 5.3 汇总 UMS↔TRADE 与 GMS 的目标依赖图，给出“暂不拆分”的正式决策，以及后续若要拆分时应先完成的最小契约和测试准备清单。
