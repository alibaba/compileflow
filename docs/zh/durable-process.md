# Durable Process

Durable Process 为流程提供持久化执行能力，由一个 `DurableStore` 作为唯一状态源。内置实现支持 PostgreSQL 和 MySQL。Durable 与 `ProcessEngine` 相互独立，不会自动持久化普通流程调用，也不会将应用版本写入流程身份。

## 添加依赖

```xml
<dependency>
  <groupId>com.alibaba.compileflow</groupId>
  <artifactId>compileflow-tbbpm</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>com.alibaba.compileflow</groupId>
  <artifactId>compileflow-durable-spring-boot-starter-postgresql</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Durable Starter 只选择存储实现，不会自动引入流程格式模块。应用需要显式添加实际使用的格式模块：仅使用 BPMN 时，将 `compileflow-tbbpm` 替换为 `compileflow-bpmn`；两种格式都需要时，同时添加两个模块。

从源码构建时，先安装所选格式模块、Starter 及其依赖：

```bash
./mvnw install -pl compileflow-tbbpm,compileflow-durable/compileflow-durable-spring-boot-starter-postgresql -am -DskipTests
```

```yaml
compileflow:
    durable:
        enabled: true
        database:
            migrate: true
```

使用 MySQL 8.4 时，改用 `compileflow-durable-spring-boot-starter-mysql`，数据库配置项不变。不要同时引入两个存储 Starter；确有需要时，必须通过 `compileflow.durable.database.provider` 明确选择一个实现。

配置与所选存储实现匹配的 `DataSource`。应用同时存在业务数据源时，将 Durable 数据源声明为名为 `compileFlowDurableDataSource` 的 Bean；否则使用唯一或标记为 Primary 的 `DataSource`。

生产环境通常应由独立的 Flyway 任务执行数据库迁移，并将 `migrate` 设为 `false`。启动时会校验数据库类型、结构和待执行迁移，任何不匹配都会阻止启动。DDL 应使用独立的迁移账号。MySQL 开启 binary logging 后，创建不可变性 Trigger 可能需要更高权限；这些权限不应授予运行时账号，也不应通过放宽全局信任设置规避。运行时账号只需要结构校验和 DML 权限。

## 配置精确版本来源

```java
ProcessRef.Version process = ProcessRef.version("sales", "approval", "v17");
```

`DurableVersionDefinitionSource` 为通过 Version 或 Alias 启动的新流程提供权威定义。返回的 `VersionDefinition` 包含类型明确的 `ProcessDefinition.Inline` 和精确的 `callBindings`。`callBindings` 用于证明源码中的每个直接 ProcessCall 都与发布时固定的子流程版本一致，不能作为另一套可修改的执行图。

Durable 以 UTF-8 保存流程内容并计算 SHA-256，同时固化模型类型、流程编码、原始字节和摘要。由内容生成的 `processId` 用于恢复和运行时缓存；namespace 与 Version 只记录启动来源。恢复时直接读取已存储的流程内容，不再访问版本来源。每份流程定义自行声明模型类型。精确版本调用可以跨格式模块，并在流程启动前校验输入输出契约；classpath 调用沿用调用方的格式。

Durable 只接受文档列出的 TBBPM 和 BPMN 能力，并在注册前拒绝其他模型类型或不支持的结构。数据库会保存明确的 `ProcessModelType`，恢复时不会根据 XML 内容猜测格式。两个格式模块最终使用同一套 Durable 语义模型。消息关联、用户任务、边界事件和事件网关不在支持范围内。

## 使用显式定义启动

```java
ProcessRun run = durable.start(
    ProcessRunId.random(),
    ProcessDefinition.inline(ProcessModelType.TBBPM, "approval", definitionText),
    Map.of("orderId", "o-42"));
```

调用方需要提供带模型类型的流程定义。Durable 使用独立的 `DurableProcessEngineConfig` 和 Core 定义加载器，保存原始字节及声明的模型类型，不设置引擎级默认格式。

普通 Java 应用通过 `DurableProcessEngineFactory.create(config)` 创建引擎，返回后即可调用应用 API。无参 `start()` 启动已配置的工作节点，`stop()` 等待工作节点停止，`close()` 等待已接收的操作完成并释放引擎持有的资源。存储和应用提供的能力仍由应用管理。Spring 使用同一个工厂，只负责适配生命周期；仅引入 Durable Starter 不会创建 `ProcessEngine`。

## 使用 Version 或 Alias 启动

```java
ProcessRun exact = durable.start(
    ProcessRunId.random(), process, Map.of("orderId", "o-42"));

ProcessRunId knownId = ProcessRunId.random();
ProcessRun addressable = durable.start(
    knownId, process, Map.of("orderId", "o-44"));

ProcessRun started = durable.start(
    ProcessRunId.random(),
    ProcessRef.alias("sales", "approval", "prod"),
    Map.of("orderId", "o-43"),
    new AliasRoutingOptions("customer-43", Map.of("region", "cn")));
```

API 为 Version 和 Alias 分别提供强类型重载；仅有流程编码不能启动 Durable 流程，精确 Version 也不接受路由参数。通过 Alias 启动时，引擎读取已提交的路由状态，先执行路由绑定的具名 `ProcessAliasTargetingPolicy`，未选中目标时再使用协议规定的百分比分流。路由键和属性只参与版本选择，不会随流程实例持久化。流程实例可以记录选中的 Version；工作节点和恢复过程只使用 `processId`，不会再次查询 Alias。

启动参数是所选流程定义中 `param` 变量构成的封闭 Map，可以只提供部分字段。传入 `return`、`inner` 或未声明字段会在提交流程实例前失败。缺失参数在首次执行时使用定义中的默认值；显式传入的 `null` 保持为 `null`。之后的检查点包含完整流程状态，但只由 Durable 内核提交，调用方不能重新注入。

每次启动都要求调用方预先分配 RunId，以便在发送请求前确定实例身份。RunId 不是通用幂等键：重复使用会返回 `RUN_ALREADY_EXISTS`；响应结果不确定时，应调用 `getRun(runId)` 核实。重复检测先于可变的 Alias 解析。一个 RunId 永久对应一次流程执行，即使原实例已按保留策略删除也不能复用。

如果 HTTP、MQ 或业务操作必须确保只启动一次，应用层仍需负责请求等价性判断，以及业务身份到 RunId 的映射。Durable 内核不提供通用幂等键或命令回执。

可重放 Action、Effect 输入或 Wait 描述回调进入应用代码前，流程序列化器会创建类型明确且与原状态分离的对象图，其中也包括只读容器内的可变 POJO。修改回调参数不会改变已持久化的流程状态；状态变更必须通过声明的 Action 输出映射或已提交的边界结果返回。

## 持久化类型

已存储流程会固定源码、控制语义、变量声明、Action 声明和恢复位置，但不会固定 Spring Bean、Java Action 字节码、依赖库、`ScriptExecutor` 实现或应用 POJO 的类结构。持久化变量、作用域帧、Effect 输入输出或 Wait 结果涉及的每个 Java 类型，都属于应用需要维护的持久化数据结构。

只要对应流程实例仍被保留，应用就必须保证这些值可以继续解码。CompileFlow 不持久化应用构建 ID，也不按应用版本选择恢复节点。

CompileFlow 负责解析器、语义编译器、恢复坐标和内部状态封装格式的兼容性。封装中的内部版本头只用于安全解码和数据升级，不属于流程或编解码器身份。仓库中的流程定义、continuation、Wait、Timer 和 Effect 固定样例用于验证已提交状态仍可恢复。

持久化流程要求应用、Durable 内核和存储使用匹配的协议版本；不支持不同协议版本混合运行。

## Wait 与 Complete

`WAIT_COMMITTED` 事件用于将原始的 256 位 Wait 令牌可靠地交给授权调用方。Wait 权威记录只保存 SHA-256 摘要；活动 Outbox 记录会暂存原始令牌，直到投递成功或对应 Wait 被清理。必须按凭据存储保护 Outbox，并禁止记录原始令牌。

```java
durable.completeWait(
    new WaitToken(rawToken),
    Map.of("approved", true));
```

令牌全局唯一。Durable 先根据令牌定位所属流程实例，再在以 Run 为首要锁定对象的事务中完成操作。元素和事件等恢复位置已保存在 Wait 记录中，调用方完成 Wait 时不需要也不能再次声明。该令牌是一次性、不透明的续执行凭证，不是业务身份。

受控的 RPC 或 MQ 可以原样传递令牌；外部系统只返回自身任务 ID 时，集成层可以安全保存 `externalJobId -> WaitToken` 映射。业务系统继续使用自己的业务身份和事实记录，Durable 不要求额外建立令牌表。原始令牌不得出现在指标标签、浏览器可见的 URL、第三方元数据或 Workbench 页面中。CompileFlow 不提供消息订阅、缓冲、TTL 或提前到达消息的关联能力。

完成载荷是该流程定义中变量的类型化部分更新，在提交 Wait 结果前完成校验。同一令牌和同一规范化结果的重复提交不会产生写入；不同结果会冲突；已取消或不相关的 Wait 不接受该令牌。

## Effect Action

外部 observation 用 Action 的 `execution="effect"`：

```xml
<action type="spring-bean" execution="effect" bean="payment"
        class="com.example.PaymentService" method="charge">
  <input source="requestId" target="requestId" dataType="java.lang.String"/>
  <effectPolicy recovery="reconcile"
                maxAttempts="3"
                maxReconcileAttempts="10"
                recoveryDelay="PT5S"
                maxRecoveryDuration="PT30M">
    <reconcileAction type="spring-bean" bean="payment"
                     class="com.example.PaymentService" method="queryCharge">
      <input source="requestId" target="requestId" dataType="java.lang.String"/>
      <input source="__cf_effect_id" target="effectId" dataType="java.lang.String"/>
    </reconcileAction>
  </effectPolicy>
</action>
```

Reconcile 输入只能来自原始 Effect 请求中已持久化的字段或预定义的 Effect 元数据，不能使用流程表达式。该适配器不支持默认值、输出、执行模式或调用策略；确认后的结果仍通过原 Effect Action 的输出映射写回流程变量。

未声明恢复策略时默认由人工处置。业务调用需要稳定标识时，重试和对账 Action 可以映射每次尝试共用的 `__cf_effect_id`；但仅有该映射并不能证明操作幂等。

准备流程结构时不会解析或创建应用组件。解析 Spring Bean、构造 Java 对象、执行脚本和调用方法都可能已经把请求发送到外部系统；此后发生异常、超时或工作节点丢失，只要无法确认结果，就会进入 UNKNOWN 状态。同一次不确定状态的 `unknownSince` 在多次对账领取之间保持不变，`maxRecoveryDuration` 从首次进入不确定状态开始计算。业务拒绝应通过正常的类型化返回值和 Decision 分支表达。

人工处置必须携带当前 `reviewRevision`，并且只能选择确认成功、确认未执行后重试，或将流程实例标记为失败。重复确认成功时，只有规范化结果与已提交结果一致才不会产生写入。重复标记失败也只有在该 Effect 处置确实导致流程失败时才等价；因取消流程而取消的 Effect 不能视为相同结果。

## Pause、Resume、Cancel 与 Outbox

Pause 和 Resume 是受 `expectedControlRevision` 保护的控制状态变更。正在执行的任务会先进入 `PAUSE_REQUESTED` 并等待收敛。Resume 必须基于最新控制版本，并且只能在流程实例仍可继续时生效。应用通过 `cancel(runId)` 取消流程，首次取消意图生效。认证、操作者身份和请求审计由暴露这些操作的应用边界负责。Outbox 运维操作使用 `eventId + expectedRevision` 进行并发控制。

Outbox 只暴露以下事件：

- `WAIT_COMMITTED`
- `EFFECT_REVIEW_REQUIRED`
- `RUN_SUCCEEDED`
- `RUN_FAILED`
- `RUN_CANCELLED`

`DurableOutboxSink` 是通用的外部投递适配器。投递至少一次，所有重试都携带相同的事件 ID、类型和逻辑载荷。正常返回必须表示目标系统已经持久接收事件，而不是仅写入 JVM 内存队列。Sink 必须按事件 ID 持久去重，或将事件 ID 原样传递给下游完成去重。CompileFlow 不保证外部副作用恰好执行一次；该 SPI 也不定义流程实例之间的通信协议。

## 查询

嵌入式 Java 查询和分页 API 返回类型明确的 keyset 游标（`ProcessRunCursor`、`ProcessTimelineCursor` 和 `OutboxEventCursor`）；Web 适配器可以自行编码并签名不透明分页令牌。

`getRun(runId)` 和 `listRuns(query)` 不返回业务载荷。只有 `getRunResult(runId)` 返回包含载荷的封闭结果类型 `ProcessRunResult`：`NotFound`、`NotCompleted`、`Succeeded`、`Failed` 或 `Cancelled`，从类型上区分实例不存在与尚未结束。

## 运维规则

- 所选存储的数据库时间是可用性判断和租约计算的权威时钟。
- 加锁顺序固定为先 Run，后具体 occurrence。
- 领取或重新领取任务时生成新的随机租约令牌；续租沿用现有令牌；提交结果必须匹配当前令牌。
- 不得记录 continuation、Effect 或 Wait 载荷，也不得记录原始令牌。
- 备份和恢复必须覆盖全部七张 Durable 表。执行 PITR 时先停止所有运行时，恢复后重建可丢弃的流程缓存。
- 仍被可恢复流程实例、备份或 WAL/PITR 恢复点引用的精确流程定义必须保留。可选的未使用流程清理策略只能删除没有 Run 引用的定义；Run-Process 外键和启动时对流程定义获取的 key-share lock 可保证准入与清理并发安全。
- 认证、授权、审批策略、请求去重、限流、TLS 和数据库访问控制由传输层或应用层负责。
