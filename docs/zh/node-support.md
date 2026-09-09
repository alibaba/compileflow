# CompileFlow 节点支持列表

`ProcessEngine` 与 Durable 支持的 BPMN/TBBPM 元素并不完全相同。只有语义模型和所选执行方式都实现了某个元素，该元素才可以执行。

## TBBPM 支持的节点

### 基础节点

- `start` - 开始节点
- `end` - 结束节点
- `autoTask` - 自动任务节点
- `scriptTask` - 脚本任务节点

### 网关节点

- `exclusive` - 排他网关
- `parallel` - 并行网关
- `inclusive` - 包容网关

### 流程控制

- `while` - 有界条件循环
- `foreach` - 顺序或并行集合遍历
- `subBpm` - 内嵌 BPM 作用域
- `bpmCall` - 调用另一个 BPM 定义
- `continue` - 继续节点（用于循环内部）
- `break` - 中断节点（用于循环内部）

顺序和并行 `foreach` 都支持有序输出聚合。每次迭代开始时，输出元素变量会重置为其声明的默认值；输出集合仅在循环退出时发布。

### 触发入口节点

- `waitTask` - 等待任务
- `waitEventTask` - 等待事件任务

在 `ProcessEngine` 中，它们是 `trigger(...)` 新调用的顶层具名入口，不是持久化检查点，也不能位于循环内。在 `durable-strict@1` 中，它们是持久化 Wait 边界，可以位于有界循环内；恢复时必须提供对应流程实例的一次性 Wait 令牌。

### Durable 边界与动作语义

- `timerTask` - 持久化挂起，直到固定时长、时长表达式或绝对唤醒时间表达式指定的时间到期。

`timerTask` 由 TBBPM Schema 与 Durable 编译器支持，也可以位于循环内；ProcessEngine 没有持久化调度器，因此会拒绝它。
Effect 不是节点。Durable TBBPM 模型中的每个可执行 Action 必须显式声明 `execution="replayable|effect"`。
BPMN service Action 同样要求显式声明；BPMN `scriptTask` 默认 `replayable`，也可显式选择 `cf:execution="effect"`。Effect Action 仍使用普通
Java 方法、Spring Bean 方法或已注册语言的脚本实现；Durable 内核负责持久化投递状态和执行结果，包括结果未知的状态。

Durable TBBPM 能力集支持 `start`、`end`、`autoTask`、`scriptTask`、`exclusive`、两个 Wait 节点、`timerTask`、
`while`、`foreach`、`break`、`continue`、结构化 `parallel`/`inclusive`、`subBpm` 与 `bpmCall`。Parallel/Inclusive 使用持久化的
确定性的分支边界和稳定合并顺序。流程调用产生的应用写入无法证明只属于单个分支，因此不能放在并发区域中。

Durable `bpmCall` 可以在直接调用图中声明精确的应用类路径，也可以声明精确版本；版本调用图只使用确定版本。别名只在根流程启动时解析，
此后所有静态调用位置都绑定到精确目标。子流程调用在同一个 Run 中使用新的 `ProcessInvocation` 帧执行，不会创建另一个 Run。
排他网关、While、Timer、守卫条件和连线条件会继续生成 Java 源码。Action 可以间接使用已注册的 `ScriptExecutor`，CompileFlow
不限定脚本语言。Durable While 计划允许配置 `maxIterations` 上限，TBBPM 则要求每个 `while` 都必须声明该值；
单次执行步数还会受到独立的执行轮次预算限制。

详见 [TBBPM 规范](specifications/tbbpm.md#34-durable-timer-与-effect-action)
与 [Durable Process 使用指南](durable-process.md)。

### 其他

- `note` - 注释节点

## BPMN 2.0 支持的节点

### 事件

- `startEvent` - 开始事件
- `endEvent` - 结束事件
- 带 `messageEventDefinition` 的 `intermediateCatchEvent` - 根据引用的消息名称选择 Durable Wait 边界。
- 带 `timerEventDefinition` 的 `intermediateCatchEvent` - 使用固定时长、时长表达式或绝对唤醒时间表达式的
  Durable Timer 边界；不支持 `timeCycle`。

### 任务

- `serviceTask` - 服务任务。必须恰好包含一个 `cf:action`；action 映射变量放在
  `cf:action` 内，不能直接挂在任务上。
- `scriptTask` - 脚本任务。必须提供 `scriptFormat` 和非空的标准 `<script>` 内容；映射用 `cf:input`/`cf:output` 直接挂在任务上。
- `receiveTask` - 必须提供 `messageRef`，且只能指向一个名称非空的顶层 `message` 定义。普通 `ProcessRuntime` 将其作为新调用的具名入口；Durable 将其转换为精确的持久化 Wait occurrence。该节点没有 `inAction`/`outAction` 钩子；需要在边界前后执行操作时，应使用显式任务节点。

### 网关

- `exclusiveGateway` - 排他网关
- `parallelGateway` - 并行网关
- `inclusiveGateway` - 包容网关

### 结构

- `subProcess` - 内嵌子流程。每个子流程必须是只有一个开始事件和一个结束事件的连通图。
  在 ProcessEngine 中，`receiveTask` 等触发入口仅支持放在根流程中，因为新的 `trigger(...)` invocation 不会恢复内嵌调用栈；
  Durable 持久化作用域栈，支持内嵌作用域中的 Wait。
- `callActivity` - 调用活动。必须提供 `calledElement`，并且必须且只能提供 `cf:classpath` 或 `cf:version` 之一；
  映射用 `cf:input`/`cf:output` 直接挂载，返回映射必须指定目标流程变量。

> **Workbench 执行边界：**BPMN 设计器可以在可视化编辑与 XML 转换过程中完整保留 `subProcess` 层级、内部节点和连线。浏览器模拟器不执行内嵌子流程；生成代码和实际运行语义应通过后端执行模式验证。

### 定义元数据

- `message` - 消息定义元数据。它可以被解析并保存在 BPMN model 上，但不是独立可执行节点。

### 活动包装元素

- `standardLoopCharacteristics` - 执行有条件或有次数上限的标准循环。`loopMaximum` 如存在，必须是
  `1` 到 `2147483647` 的整数；`loopCondition` 和 `loopMaximum` 至少声明一个。
- `multiInstanceLoopCharacteristics` - 顺序或确定性有序并行遍历 `cf:collection`。并行模式仅支持 Durable，且要求兼容 List 的输入。
  两种模式都可使用原子属性对 `cf:target` / `cf:source` 做可选有序聚合；顺序结果在循环退出时发布。
  生成的词法名称必须是合法 Java 标识符，`cf:itemType` 必须是合法 Java 类名。

可执行 BPMN 必须具有非空 `targetNamespace`、一个声明 `isExecutable="true"` 的 process、全局唯一
ID，并且只能包含受支持的属性和扩展数据。未知可执行数据会被拒绝，不会在规范化写回时被丢弃。BPMN process `id` 就是其
CompileFlow 流程 code，必须与 `ProcessDefinition` 携带的 code 完全一致。完整 `cf:`
语法和归属规则见 [BPMN 扩展规范](specifications/bpmn-extensions.md)。

## 不支持的 BPMN 元素

CompileFlow 不支持以下 BPMN 2.0 元素，也不提供这些元素所需的生命周期、广播或协作基础设施。包含这些元素的流程会在解析或预检阶段失败，不会以缺失部分语义的方式运行。

### 未提供所需的运行时基础设施

这些元素需要 CompileFlow 不提供的运行时基础设施：

- `userTask` — 需要持久化任务存储以及领取和完成生命周期。应用负责管理这些状态；只有在从具名入口启动一次新调用已经足够时，才使用 TBBPM `waitTask` 与 `trigger`。
- `manualTask` — 与 `userTask` 具有相同的持久化约束。
- `businessRuleTask` — 需要决策表引擎。请使用 `serviceTask` + Java action。
- `sendTask` — 需要具有发送/接收语义的消息系统。请使用 `serviceTask`。
- `boundaryEvent` — 需要按实例活动状态跟踪用于错误/补偿/定时器边界。
- `intermediateThrowEvent` — 需要带有活跃订阅者的事件分发器。
- `signal` — 需要信号注册表和运行时订阅（有状态）。
- 事件定义（`cancelEventDefinition`、`compensateEventDefinition`、`conditionalEventDefinition`、
  `errorEventDefinition`、`escalationEventDefinition`、`linkEventDefinition`、`signalEventDefinition`、
  `terminateEventDefinition`）— 都需要 CompileFlow 不提供的事件基础设施。

### 协作与编排

CompileFlow 执行单流程；协作/编排元素超出范围：

- `choreography`、`choreographyTask`、`subChoreography`
- `callConversation`、`conversation`、`subConversation`
- `globalBusinessRuleTask`、`globalConversation`、`globalManualTask`、`globalScriptTask`、`globalUserTask`
- `partnerEntity`、`partnerRole`、`participantAssociation`、`participantMultiplicity`

### 数据存储与资源分配

`ProcessEngine` 的调用状态保存在内存中，Durable 存储也只记录 CompileFlow 自身的执行状态。两种执行方式都不实现 BPMN 数据存储或资源分配语义，这些职责由应用承担：

- `dataStore`、`dataStoreReference`、`dataAssociation`、`dataInputAssociation`
- `loopDataInputRef`、`loopDataOutputRef`
- `resource`、`resourceRole`、`potentialOwner`、`performer`、`humanPerformer`
- `assignment`、`resourceParameter`、`resourceParameterBinding`、`resourceAssignmentExpression`

### 其他不支持元素

- `transaction` — 请在服务层使用 Spring `@Transactional`。
- `complexGateway` — 需要事件条件评估基础设施。
- `eventBasedGateway` — 需要事件订阅注册表。
- `group`、`textAnnotation`、`association` — 仅图表元素，无运行时语义。
- `auditing`、`monitoring` — 使用 CompileFlow 事件、指标和应用自有可观测性。
- `category`、`categoryValue` — 图表分组；无运行时效果。
- `correlationProperty`、`correlationSubscription`、`correlationKey` — 消息关联需要消息代理。
- `error`、`escalation`、`itemDefinition`、`interface`、`operation`、
  `ioParameter`、`inputSet`、`outputSet`、`inputOutputSpecification`、`inputOutputBinding` — CompileFlow 未实现的执行语义元数据。
- `lane`、`laneSet` — 流程组织；在编译执行模型中无运行时效果。
- `multiInstanceFlowCondition` — 多实例通过 `multiInstanceLoopCharacteristics` 处理。
- `complexBehaviorDefinition` — 行为监控基础设施。
- `endPoint`、`import`、`relationship`、`rendering` — Schema 级元数据，无运行时效果。

## 相关操作指南

### 人工任务实现

应用的任务模块可以管理任务身份、持久化、授权和变量，再调用 TBBPM trigger 入口：

```xml
<waitTask id="approval" name="等待审批" g="80,0,120,48">
    <transition to="afterApproval"/>
</waitTask>
```

```java
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "approval.flow", "flows/approval.flow.bpm"),
        ProcessTrigger.at("approval"),
        approvalData);
```

该调用从指定入口启动一次新执行；CompileFlow 不存储或恢复之前的调用状态。

### Durable 人工任务集成

对于持久化流程实例，应将人工步骤建模为 `waitTask`。Durable 内核提交 Wait 及其 `WAIT_COMMITTED` Outbox 事件。应用的任务模块可以据此创建和管理任务，包括分配、授权、表单、评论和 SLA 策略。完成授权并按事件去重后，通过 `DurableProcessEngine.completeWait(...)` 完成对应的 Wait。

```java
durable.completeWait(waitToken, Map.of("approved", true));
```

`waitToken` 是持有者凭证，必须按敏感凭据进行保护。CompileFlow 支持将人工处理接入流程，但不内置 `humanTask` 节点或人工任务管理模块。

### 进程内定时任务

ProcessEngine 流程由应用调度器发起。若要在同一个 Durable Run 中持久化延迟，请改用 `timerTask`：

```java
@Scheduled(fixedDelayString = "${jobs.scheduled-flow.delay:PT1M}")
public void scheduledTask() {
    engine.execute(ProcessDefinition.classpath(ProcessModelType.TBBPM, "scheduled.flow", "flows/scheduled.flow.bpm"), Map.of()).orElseThrow();
}
```

固定延迟可以避免同一个调度器实例重叠执行。分布式部署若要求整个集群只发起一次调用，仍需由应用提供单一所有者机制或幂等策略。

## 实现依据

以下实现共同定义运行时支持边界：

- TBBPM 解析器注册表：[`TbbpmElementParserRegistry`](../../compileflow-tbbpm/src/main/java/com/alibaba/compileflow/engine/tbbpm/parser/TbbpmElementParserRegistry.java)
- TBBPM 语义前端：[`TbbpmSemanticFrontend`](../../compileflow-tbbpm/src/main/java/com/alibaba/compileflow/engine/tbbpm/semantic/TbbpmSemanticFrontend.java)
- BPMN 解析器注册表：[`BpmnElementParserRegistry`](../../compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/parser/BpmnElementParserRegistry.java)
- BPMN 语义前端：[`BpmnSemanticFrontend`](../../compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/semantic/BpmnSemanticFrontend.java)
- ProcessEngine 代码生成器：[`JavaProcessCodeGenerator`](../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/java/codegen/JavaProcessCodeGenerator.java)
- Durable 状态机转换器：[`DurableMachineLowerer`](../../compileflow-durable/compileflow-durable-runtime/src/main/java/com/alibaba/compileflow/durable/runtime/machine/DurableMachineLowerer.java)

仅有解析器并不代表元素可执行。BPMN `message` 和循环特征等元素只是元数据或包装结构，不是独立运行时节点。
`DurableMachineLowerer` 将 Durable 专属语义转换为持久化状态机，不经过普通 ProcessEngine 运行时。受支持节点必须具备一致的解析、
源码校验、语义转换、运行时实现、测试和文档。
