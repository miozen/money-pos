# AD-6：Java 17 源码基线升级评估

## 结论

**批准把主 Maven reactor 的最低构建与运行基线统一为 Java 17，但本任务不实施。**

这不是 Spring Boot 3 升级、不是 `javax` → `jakarta` 迁移、不是 Maven 模块拆分，也不要求
立即采用 `record`、文本块或其他 Java 17 语法。目标是让编译契约、测试 JVM 和 Windows 随包 JRE
表达同一个最低版本：**Java 17**。

下一项最小实施切片应为 **AD-6.1：主 reactor Java 17 构建基线实施**，只修改构建治理文件并完成
隔离数据库回归与 Windows 打包验证；未经单独授权不得执行。

## 当前事实

| 面 | 证据 | 结论 |
| --- | --- | --- |
| 编译声明 | 根 `money-pos/pom.xml` 同时设定 `java.version`、`maven.compiler.source`、`maven.compiler.target` 为 `1.8`。 | POM 仍声称 Java 8。 |
| 实际打包运行时 | `.github/workflows/build-exe.yml` 使用 Temurin 17，并下载/随包使用 Temurin JRE 17；Electron 只会拉起随包 `vana-java.exe`。 | 交付的 POS 已要求 Java 17，不存在 Java 8 桌面客户机兼容承诺。 |
| 本地构建环境 | WSL 的 Maven 3.8.7 运行于 OpenJDK 17.0.20。 | 开发环境已与目标基线一致。 |
| 框架 | 父 BOM 为 Spring Boot 2.7.18。该版本官方要求 Java 8 并兼容至 Java 21。 | Java 17 不是框架阻塞项。 |
| 字节码 | 当前 fat JAR 内 `QkMoneyApplication.class` 的 major version 是 52（Java 8）。 | 产物字节码尚未升为 Java 17。 |
| 实际 API | 生产源码已有 `List.of` / `Map.of`：会员资产、会员模板、商品 Excel 和财务会员资产查询；反编译的 class 52 直接调用 `java.util.List.of`。 | Java 8 运行兼容性已经失真：Java 8 会在对应路径发生链接错误。 |
| Web 命名空间 | 主 reactor 生产源码有 65 个 `javax.*` import、0 个 `jakarta.*` import。 | 必须留在 Spring Boot 2.7 / Spring Framework 5 的 `javax` 体系；AD-6 不触发 Boot 3。 |
| 测试与发布 | 主 reactor 的 60 个测试类全部使用 Spring 测试注解；Windows EXE 构建使用 `mvn clean package -DskipTests`。 | Java 17 编译/打包已有发布证据，但完整回归仍必须在隔离 `money_pos_test` 中执行。 |
| 旁路工程 | `xxl-job-admin` 不在根 reactor modules 中，自带 Spring Boot 2.6.7、Java 8 设定且默认跳过测试。 | 不纳入 AD-6；如仍需交付或运行，另立升级/处置任务。 |

`source` / `target` 只控制语言和 class 文件级别，不能阻止编译器在较新 JDK 上引用更高版本 API；
Apache Maven 明确建议使用 `release` 来避免这种运行期链接错误。当前项目的 class 52 + `List.of` 正是
该风险的实证，而不是假设。

## 方案比较

| 方案 | 收益 | 风险 / 缺口 | 决定 |
| --- | --- | --- | --- |
| A. 保持 Java 8 声明 | 不改构建文件。 | 与随包 JRE 17 和实际 Java 9 API 矛盾；无法给 Java 8 运行提供真实保障。 | 拒绝。 |
| B. 继续编译为 Java 8，但改用 `release=8` | 可真实发现高版本 API。 | 会立刻拒绝现有生产 `List.of` / `Map.of`；若修回 Java 8，还会继续维护已无交付价值的旧基线。 | 不推荐。 |
| C. 统一为 Java 17，仍维持 Boot 2.7 | 使源码、测试、CI 打包和随包 JRE 一致；消除假兼容性；不改变应用架构。 | 需要一次完整回归与 Windows 包复验；不自动升级依赖。 | **推荐。** |
| D. Java 17 + Boot 3 / Jakarta | 获得更现代的长期框架线。 | 需全量 `javax` 迁移、Spring Security/MyBatis/Flyway/Swagger/XXL 兼容治理，远超基线统一。 | 明确排除。 |

## AD-6.1 的固定范围

仅在用户授权后实施以下构建治理改动：

1. 主 reactor 根 POM 将 Java 版本表达统一为 `17`，并用显式、受管的 Maven Compiler Plugin
   `release=17` 编译，避免继续依赖 Maven 的旧默认插件行为。
2. 将最低 Java 版本检查固化为构建规则；开发机、CI 与发布包都应在低于 17 时明确失败。
3. 审查并按需要更新 Surefire 的 Java 17 支持版本，但不改变测试语义、数据库配置或测试选择。
4. 在现有 Windows EXE 流程中保留 Temurin 17；不下载第二套 JDK，不改 Electron、MariaDB 或安装结构。

不包含：Spring Boot 3、`javax`/`jakarta`、依赖版本批量升级、业务源码重写、Maven Feature 拆分、SQLite、
启动裁剪、`xxl-job-admin` 或任何数据库/Flyway/路由变更。

## 验收与回滚

AD-6.1 必须依次证明：

1. 在 Java 17 下从聚合根执行隔离 `money_pos_test` 全量回归；
2. `mvn -q package -DskipTests` 成功，fat JAR 应为 major version 61；
3. 架构扫描夹具、`architecture-scan.sh --check-new` 与空白检查通过；
4. GitHub Windows EXE 构建成功，安装包仍使用唯一的 Temurin JRE 17；
5. ENV-1 已有数据启动/health/收银窗口最少做一次回归确认；不把它伪装成性能优化结果。

回滚只恢复 AD-6.1 的 POM/构建治理提交；不触碰已生成的数据目录、数据库结构或 Electron 安装目录。

## 已知限制

- 当前仓库没有 Maven Wrapper、toolchain 或 Enforcer 规则，构建工具版本与 JDK 最低要求没有被仓库自证。
- `maven-surefire-plugin` 仅在 `money-app-biz` 固定为 2.22.2；是否随基线升级应由 AD-6.1 的实际 Java 17
  回归决定，不能在评估阶段猜测修改。
- 本地依赖插件缓存缺少 Maven Dependency Plugin，故本轮未伪造完整 resolved dependency tree；已基于
  声明 BOM、已打包 JAR、实际 JDK 与发布工作流作出结论。实施前的 Java 17 全量构建将提供最终依赖验证。

## 参考

- [Spring Boot 2.7.18 system requirements](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements)
- [Maven Compiler Plugin: source/target API pitfall](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-source-and-target.html)
- [Maven Compiler Plugin: `release`](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)
