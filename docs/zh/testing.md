# CompileFlow 测试指南

仓库测试遵循以下边界、编写原则和验证命令。依赖版本以根 `pom.xml`、Spring Boot BOM 和
`compileflow-workbench/pnpm-lock.yaml` 为准，不在此重复容易过期的版本号。

## 核心原则

- 测试行为和公开契约，不复制实现步骤。
- 测试必须可重复、可并行理解，并且不依赖执行顺序、静态残留状态或本机环境。
- 测试范围与改动风险匹配。局部逻辑运行定向单元测试；跨模块、持久化或交付形态改动扩大验证范围。
- 失败必须提供可诊断证据。断言业务结果、错误码和持久化状态，不只断言"没有抛异常"。
- 测试负责释放线程池、类加载器、数据库连接、临时目录和子进程。
- Java 测试优先使用 AssertJ，保持断言与失败诊断一致。只有集成框架 API 明确要求时才使用框架原生 assertion；同一个测试中不要混用多种断言风格。
- 不把覆盖率数字当作完成标准。覆盖关键分支、边界条件和曾经发生的回归。

## 测试层级

| 层级           | 位置                                                    | 目标                                                     | 外部依赖                                   |
|----------------|---------------------------------------------------------|----------------------------------------------------------|--------------------------------------------|
| 单元测试       | 各模块 `src/test`                                       | 单个类型或小型协作边界                                   | 无网络、无真实数据库                       |
| 组件测试       | 各模块 `src/test`                                       | parser、repository、Spring auto-configuration 等模块边界 | 可使用内存 fixture 或受控 Spring context   |
| 集成测试       | `compileflow-integration-tests`                         | Engine、格式模块、Spring 和 Deploy 的跨模块契约          | 按测试声明提供                             |
| Server 测试    | `compileflow-workbench-server/src/test`                 | REST、认证、持久化、路由和可观测性                       | H2 仅限测试；PostgreSQL 验证真实持久化契约 |
| 交付烟测       | `scripts/smoke_test_workbench_server_executable_jar.py` | 最终 Spring Boot 可执行 JAR 的启动、动态编译和执行       | 独立 Server 子进程与 PostgreSQL            |
| Workbench 测试 | `compileflow-workbench`                                 | TypeScript 单元、契约和 Playwright 工作流                | 仅使用 `pnpm`                              |

测试不能用较低层级替代较高层级。例如，exploded Spring test context 通过，不能证明
`java -jar` 下的 nested JAR 类路径可用于动态编译。

## Java 测试

### 命名和结构

测试名应描述可观察行为，例如：

```java
@Test
void staleReconciliationDoesNotReloadCompletedVersion() {
    // Arrange only the state needed to expose the race.
    // Act through the public or owned module boundary.
    // Assert the externally visible invariant.
}
```

- 不强制统一成冗长的 `should...When...` 模板。
- `@Nested` 和 `@DisplayName` 只在确实改善组织或报告时使用。
- Arrange/Act/Assert 注释只用于长测试；短测试应通过代码结构表达阶段。
- 一个测试可以包含多个共同证明同一行为的断言，不要求"一测试一断言"。

### 断言

```java
assertThat(result.getOutput()).containsEntry("status", "completed");

assertThatThrownBy(() -> service.publish(invalidRequest))
        .isInstanceOf(DeploymentException.class)
        .hasMessageContaining("version");
```

仅在失败信息缺少业务上下文时添加 `.as(...)`。不要给每条自解释断言重复添加描述。

异常测试应优先验证稳定契约：异常类型、错误码和关键上下文。除非完整消息本身是公开契约，不要断言整段可变文本。

### 并发测试

- 使用 latch、barrier、受控 executor 或测试 double 精确安排竞争窗口。
- 不使用 `Thread.sleep` 猜测另一个线程已经运行到某处。
- 超时只作为防止测试永久挂起的上限，不能作为正确性的主要断言。
- 同时验证成功结果、调用次数、失败隔离和资源回收。
- 回归测试必须在修复前能稳定暴露原问题，而不是依赖高次数随机循环。

参考
[`RuntimeInstallerReconciliationTest`](../../compileflow-deploy/compileflow-deploy-runtime/src/test/java/com/alibaba/compileflow/deploy/runtime/install/RuntimeInstallerReconciliationTest.java)。

### 类加载和资源生命周期

动态编译测试必须区分资源发现和资源所有权。测试应验证调用方传入的 `ClassLoader` 在编译完成后仍可用，并覆盖目录
classpath、普通 JAR 和最终可执行 JAR。

参考
[`GeneratedCodeCompilerTest`](../../compileflow-core/src/test/java/com/alibaba/compileflow/engine/core/java/compiler/GeneratedCodeCompilerTest.java)。

### 时间和性能

普通 CI runner 上的毫秒阈值易抖动，不能作为微基准。单元测试可以验证：

- 有界队列、并发许可和缓存上限配置生效；
- 算法不会进行无界重试或重复加载；
- 超时、取消和退避使用可控 clock/scheduler；
- 复杂度回归可通过操作次数或输入规模关系证明。

需要比较吞吐、延迟或分配率时，应使用独立、可预热、可复现的基准方案，并在报告中记录 JDK、硬件、参数和样本分布。

## 持久化测试

- Flyway migration 是 schema 的唯一权威。
- H2 只用于快速测试，不能证明 PostgreSQL 方言、锁或事务语义；`dev` profile 使用 PostgreSQL。
- SQL、并发 claim、`FOR UPDATE SKIP LOCKED`、约束和迁移必须在受支持的 PostgreSQL 版本上验证。
- 每个测试独立创建或清理状态，不依赖测试方法执行顺序。
- 不允许持久化失败后静默退回内存实现。

CI 会针对固定的 PostgreSQL 16、17、18 镜像验证 Deploy 与 Workbench 的 migration、并发、持久化和外部 schema 准入。Java
17/21/25 Server 运行矩阵是另一组独立门禁，使用 PostgreSQL 18；这两组矩阵不代表覆盖了所有 JVM 与数据库组合。

## Spring 测试

- 属性绑定和条件装配优先使用 `ApplicationContextRunner`，只加载相关 auto-configuration。
- Web contract 使用 MVC 测试或小型 Spring context；只有跨层行为才启动完整应用。
- 同时测试默认值、显式值、非法值、未知字段和缺失必需依赖。
- context 必须由测试框架关闭；不要把 Spring bean 放入跨测试静态 holder。

[`EmbeddedDeploymentExecutionIntegrationTest`](../../compileflow-workbench-server/src/test/java/com/alibaba/compileflow/workbench/server/deployment/EmbeddedDeploymentExecutionIntegrationTest.java)
验证 exploded Spring 应用中的部署执行链路；它与可执行 JAR 烟测互补，不能互相替代。

## 定向验证命令

禁止无范围运行根目录 `./mvnw test`。本地先运行最小充分范围：

```bash
# 单个模块中的一个测试类
./mvnw test -pl compileflow-core -Dtest=GeneratedCodeCompilerTest

# 改动同时涉及依赖模块时，从 reactor 构建依赖
./mvnw test -pl compileflow-core -am \
  -Dtest=GeneratedCodeCompilerTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Server 相关测试
./mvnw test -pl compileflow-workbench-server -am \
  -Dtest=EmbeddedDeploymentExecutionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# 静态质量门禁
./mvnw checkstyle:check -pl compileflow-workbench-server -am
./mvnw install -pl compileflow-workbench-server -am -DskipTests
./mvnw spotbugs:check -pl compileflow-workbench-server -am
python3 scripts/verify_spotbugs_reports.py \
  compileflow-api \
  compileflow-core \
  compileflow-tbbpm \
  compileflow-bpmn \
  compileflow-deploy/compileflow-deploy-api \
  compileflow-deploy/compileflow-deploy-control-plane \
  compileflow-deploy/compileflow-deploy-runtime \
  compileflow-spring-boot-autoconfigure \
  compileflow-workbench-server
python3 scripts/check_internal_links.py
```

`-am` 会让 Maven 同时经过上游模块。使用 `-Dtest` 时设置
`-Dsurefire.failIfNoSpecifiedTests=false`，避免没有该测试类的上游模块误报失败。SpotBugs 前的 `install` 不能省略：分析插件需要当前
reactor 的依赖 JAR；随后必须验证 XML 报告，防止插件在缺失类时仍返回成功。

## 可执行 JAR 烟测

先生成最终交付物，再运行烟测：

```bash
./mvnw package -pl compileflow-workbench-server -am -DskipTests
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/compileflow_smoke
export SPRING_DATASOURCE_USERNAME=compileflow
export SPRING_DATASOURCE_PASSWORD=replace-with-a-local-test-password
python3 scripts/smoke_test_workbench_server_executable_jar.py
```

数据库必须已创建，配置的用户必须拥有该数据库。该烟测有意使用生产持久化引擎；H2 只存在于 test scope，不会打入可执行 JAR。

烟测会自动：

1. 启动打包后的 Spring Boot JAR；
2. 等待 health endpoint ready；
3. 创建并发布一个调用 nested dependency 中 Commons Lang 的 TBBPM 流程；
4. 通过控制面/数据面链路部署该版本；
5. 执行已收敛的 `dev` alias，断言结果和路由归因；
6. 再次检查服务健康状态；
7. 终止子进程，并在失败时输出完整服务日志。

Server CI 在 JDK 17、21 和 25 上都执行该门禁。它同时验证 `jdk.compiler`、nested JAR 类路径、调用方类加载器生命周期、REST
部署和运行时执行。

## Workbench

从 `compileflow-workbench/` 运行仓库脚本，只使用 `pnpm`：

```bash
pnpm type-check
pnpm check:workbench-server-contract
pnpm test
pnpm test:e2e:smoke
pnpm test:e2e:integration
```

默认情况下，集成命令会启动并停止 Workbench Server 的捆绑 JAR 及一个仅监听 loopback 的测试 edge。先用
`pnpm verify:delivery --assembly-only` 构建该产物，并为一个已存在的 PostgreSQL 数据库提供
`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和
`SPRING_DATASOURCE_PASSWORD`。同时设置一个长度为 32..256 的 URL-safe
`COMPILEFLOW_E2E_SERVER_API_KEY`；托管 Server 使用 `prod` profile，并用该 key 执行 fail-closed 认证。测试 edge 会剥离浏览器提供的
credential 和 forwarding header，再在私有上游链路注入该 key。它只模拟这条信任边界，不是生产认证网关。

托管套件通过公开 HTTP 和浏览器界面走完整部署生命周期：不可变发布与重放、基线部署、创建灰度、stable/candidate
执行、健康评估、提升、回滚和最终 effective version 归因。灰度 cohort 使用运行时完全一致的路由 hash
选择，不依赖重复随机请求。交付验证还会逐项比较捆绑 JAR 与当前 production Web build 的静态文件，增量构建残留会直接使门禁失败。

设置 `COMPILEFLOW_E2E_SERVER_URL` 可测试已经运行的 Server API；设置
`COMPILEFLOW_E2E_BROWSER_URL` 可测试已经运行的浏览器侧网关及 UI。Playwright 不会管理这些外部进程。直接选择的 Server 需要
API key 时，再设置 `COMPILEFLOW_E2E_SERVER_API_KEY`。目标不可达时，对应测试会失败，不会静默跳过。

具体可用命令以该目录 `package.json` 和
[Workbench 贡献指南](../../compileflow-workbench/CONTRIBUTING.md)为准。前后端 contract 改动必须重新生成 Server OpenAPI
snapshot、检查 Workbench 生成类型，并通过 shared-contract parity 断言。

## CI 和失败处理

CI 至少覆盖：

- 使用 JDK 17 构建源码并运行目标测试，证明最低构建与运行边界；
- 使用 JDK 17 对 Java 17 产物执行 Checkstyle、SpotBugs、公开 API Javadoc、打包与交付门禁；
- Java 17 完整集成与 Server 行为验证，并在 Java 25 运行定向运行时兼容性 smoke；
- PostgreSQL 16/17/18 Deploy 与 Workbench migration、并发和持久化契约；
- JDK 17 与 JDK 25 可执行 JAR 烟测；
- Workbench typecheck、单元测试、Playwright smoke，以及针对以 PostgreSQL 为持久化后端的捆绑 Server 产物的认证浏览器与 HTTP
  测试；
- 仓库卫生、内部链接和供应链门禁。

测试偶发失败不是"重跑即可"。先保存 seed、输入、线程状态和日志，确定是否为产品竞争条件、资源泄漏、
环境依赖或测试自身不确定性。修复后增加确定性回归测试；不要通过扩大 sleep、放宽断言或无限重试隐藏问题。

## 提交前检查

- 新行为有对应测试，修复过的缺陷有回归测试。
- 测试不依赖顺序、网络、用户目录、默认时区或静态残留状态。
- 并发测试使用确定性同步点，所有 executor 和子进程都被关闭。
- 持久化语义在 PostgreSQL 上验证，H2 没有被当作生产替代。
- 公开 API、配置、REST 或交付形态变更覆盖了相应契约层级。
- 运行了受影响模块的目标测试和静态门禁。
- 构建生成物未进入工作树；`python3 scripts/check_internal_links.py` 通过。
