# CompileFlow 节点支持列表

`ProcessEngine` runtime 与可选 Durable 执行面支持不同的 BPMN/TBBPM 元素。一个流程元素只有在所选执行面
同时具备语义模型和该节点实现时才属于可执行节点；某个执行面支持不代表另一个执行面也支持。

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

在`ProcessEngine` 中，它们是新 `trigger(...)` invocation 的顶层具名入口，不是持久化检查点，也不能位于循环
内。在 `durable-strict@1` 中，它们是持久化 Wait 边界，可以位于有界循环内；恢复时必须提供该精确 Run 的一次性 Wait token。

### Durable 边界与 Action 语义

- `timerTask` - 持久化挂起，直到字面 duration、duration 表达式或绝对唤醒时间表达式到期。

`timerTask` 由 TBBPM Schema 与 Durable 编译器支持，也可以位于循环内；ProcessEngine 没有持久化调度器，因此会拒绝它。
Effect 不是节点。Durable 模型中的每个可执行 Action 必须显式声明 `execution="replayable|effect"`。Effect Action 仍使用普通
Java、Bean、Inline 或已注册脚本实现；Kernel 只持久化管理 dispatch 与 unknown outcome。

Durable TBBPM profile 支持 `start`、`end`、`autoTask`、`scriptTask`、`exclusive`、两个 Wait 节点、`timerTask`、
`while`、`foreach`、`break`、`continue`、结构化 `parallel`/`inclusive`、`subBpm` 与 `bpmCall`。Parallel/Inclusive 使用持久化的
deterministic frontier 与稳定 merge 顺序；process call 的应用写入无法证明为 branch-local，因此不能位于 concurrent region。
Durable `bpmCall` 在 Direct graph 中声明精确的应用 classpath 路径，也可以声明 exact child Version；精确
Version graph 只使用 Version 依赖；
Alias 只在 root admission 解析，所有静态 call-site binding 此后保持 exact。调用作为同一 Run 内的另一个
`ProcessInvocation` frame 执行，不创建 Child Run。排他网关、While、Timer、guard 与 transition 表达式继续拼接生成 Java 源码。Action type
可以间接使用已注册的 `ScriptExecutor`；CompileFlow 不固定某一种脚本语言。与源格式无关的 Durable While plan 允许可选的
`maxIterations` guard，但 TBBPM 要求每个 `while` 都必须声明它；Durable Turn budget 独立于该源语言规则，始终限制单个执行 slice。

详见 [TBBPM 规范](../specs/tbbpm-specification.zh.md#34-durable-timer-与-effect-action)
与 [Durable Process 使用指南](durable-process.md)。

### 其他

- `note` - 注释节点

## BPMN 2.0 支持的节点

### 事件

- `startEvent` - 开始事件
- `endEvent` - 结束事件
- 带 `messageEventDefinition` 的 `intermediateCatchEvent` - 按引用 message name 选择的 Durable Wait 边界。
- 带 `timerEventDefinition` 的 `intermediateCatchEvent` - 使用字面 duration、duration 表达式或绝对唤醒时间表达式的
  Durable Timer 边界；不支持 `timeCycle`。

### 任务

- `serviceTask` - 服务任务。必须恰好包含一个 `cf:action`；action 映射变量放在
  `cf:action` 内，不能直接挂在任务上。
- `scriptTask` - 脚本任务。必须提供 `scriptFormat` 和非空的标准 `<script>` 内容；映射用 `cf:var` 直接挂在任务上。
- `receiveTask` - 必须提供 `messageRef`，且必须恰好指向一个具有非空名称的顶层 `message` 定义。普通
  `ProcessRuntime` 把它作为新 invocation 的具名入口；Durable 把它 lower 为精确持久化 Wait occurrence。它没有
  `inAction`/`outAction` hook；需要边界前后工作时使用显式 task。

### 网关

- `exclusiveGateway` - 排他网关
- `parallelGateway` - 并行网关
- `inclusiveGateway` - 包容网关

### 结构

- `subProcess` - 内嵌子流程。每个子流程必须是只有一个开始事件和一个结束事件的连通图。
  `receiveTask` 等触发入口仅支持放在根流程中，因为新的 `trigger(...)` invocation 不会恢复内嵌调用栈。
- `callActivity` - 调用活动。必须提供 `calledElement`，并且必须且只能提供 `cf:classpath` 或 `cf:version` 之一；
  映射用 `cf:var` 直接挂载，返回映射必须指定目标流程变量。

> **Workbench 执行边界：**BPMN 设计器可在可视化与 XML 往返过程中完整保留并编辑
> `subProcess` 层级、内部节点和连线。浏览器模拟器对内嵌子流程执行采用失败关闭策略；
> 生成代码及真实运行语义请使用后端执行模式验证。

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
语法和归属规则见 [BPMN 扩展规范](../specs/bpmn-extension-specification.zh.md)。

## 已移除的节点

以下 BPMN 2.0 元素不被 CompileFlow 支持，其定义类已从代码库中移除。CompileFlow 不提供这些元素所需的产品生命周期、
广播或协作基础设施。包含这些元素的文件会在解析或 preflight 阶段失败；引擎不会部署静默缺失行为的流程。

### 已移除 — 架构不兼容

这些元素需要 CompileFlow 不提供的运行时基础设施：

- `userTask` — 需要持久化任务存储及领取/完成生命周期，应使用持久化外部任务系统；只有在“从具名入口启动一次新
  invocation”已经足够时，才使用 TBBPM `waitTask` + `trigger`。
- `manualTask` — 与 `userTask` 具有相同的持久化约束。
- `businessRuleTask` — 需要决策表引擎。请使用 `serviceTask` + Java action。
- `sendTask` — 需要具有发送/接收语义的消息系统。请使用 `serviceTask`。
- `boundaryEvent` — 需要按实例活动状态跟踪用于错误/补偿/定时器边界。
- `intermediateThrowEvent` — 需要带有活跃订阅者的事件分发器。
- `signal` — 需要信号注册表和运行时订阅（有状态）。
- 事件定义（`cancelEventDefinition`、`compensateEventDefinition`、`conditionalEventDefinition`、
  `errorEventDefinition`、`escalationEventDefinition`、`linkEventDefinition`、`signalEventDefinition`、
  `terminateEventDefinition`）— 都需要 CompileFlow 不提供的事件基础设施。

### 已移除 — 协作域不适用

CompileFlow 执行单流程；协作/编排元素超出范围：

- `choreography`、`choreographyTask`、`subChoreography`
- `callConversation`、`conversation`、`subConversation`
- `globalBusinessRuleTask`、`globalConversation`、`globalManualTask`、`globalScriptTask`、`globalUserTask`
- `partnerEntity`、`partnerRole`、`participantAssociation`、`participantMultiplicity`

### 已移除 — 持久化/资源层不适用

ProcessEngine runtime 的 invocation state 位于内存中，Durable Store 也只持久化 CompileFlow 自身的执行记录。两个执行面都不实现
BPMN data store 或 resource assignment 语义；这些职责由应用承担：

- `dataStore`、`dataStoreReference`、`dataAssociation`、`dataInputAssociation`
- `loopDataInputRef`、`loopDataOutputRef`
- `resource`、`resourceRole`、`potentialOwner`、`performer`、`humanPerformer`
- `assignment`、`resourceParameter`、`resourceParameterBinding`、`resourceAssignmentExpression`

### 已移除 — 其他

- `transaction` — 请在服务层使用 Spring `@Transactional`。
- `complexGateway` — 需要事件条件评估基础设施。
- `eventBasedGateway` — 需要事件订阅注册表。
- `group`、`textAnnotation`、`association` — 仅图表元素，无运行时语义。
- `auditing`、`monitoring` — 可观测性通过外部 APM 系统处理。
- `category`、`categoryValue` — 图表分组；无运行时效果。
- `correlationProperty`、`correlationSubscription`、`correlationKey` — 消息关联需要消息代理。
- `error`、`escalation`、`itemDefinition`、`interface`、`operation`、
  `ioParameter`、`inputSet`、`outputSet`、`inputOutputSpecification`、`inputOutputBinding` — CompileFlow 未实现的执行语义元数据。
- `lane`、`laneSet` — 流程组织；在编译执行模型中无运行时效果。
- `multiInstanceFlowCondition` — 多实例通过 `multiInstanceLoopCharacteristics` 处理。
- `complexBehaviorDefinition` — 行为监控基础设施。
- `endPoint`、`import`、`relationship`、`rendering` — Schema 级元数据，无运行时效果。

## 替代方案

### 人工任务实现

外部任务系统可以持有任务 identity、持久化、授权和变量，再调用 TBBPM trigger 入口：

```xml
<waitTask id="approval" name="等待审批" g="80,0,120,48">
    <transition to="afterApproval"/>
</waitTask>
```

```java
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessDefinition.classpath("approval.flow", "flows/approval.flow.bpm"),
        ProcessTrigger.at("approval"),
        approvalData);
```

该调用从入口启动一次新执行；CompileFlow 不存储或恢复先前 invocation。

### Durable 人在回路编排

对于持久化 Run，应将人工步骤建模为 `waitTask`。Durable 内核提交 Wait 及其 `WAIT_COMMITTED` Outbox 事件。
外部任务服务可以据此创建并管理自己的任务，包括分配、授权、表单、评论和 SLA 策略。外部服务完成授权并
按事件去重后，通过 `DurableProcessEngine.completeWait(...)` 完成精确的 Wait 实例。

```java
durable.completeWait(waitToken, Map.of("approved", true));
```

`waitToken` 是 bearer capability，必须按凭证保护。CompileFlow 提供的是人在回路编排，不是内建的
`humanTask` 节点或 Human Task Management 服务。

### ProcessEngine Runtime 定时任务实现

ProcessEngine 流程可以通过 Spring Scheduler、Quartz 等外部调度系统调用。若要在同一个 Durable Run 中持久化延迟，请改用 `timerTask`：

```java
@Scheduled(fixedDelayString = "${jobs.scheduled-flow.delay:PT1M}")
public void scheduledTask() {
    engine.execute(ProcessDefinition.classpath("scheduled.flow", "flows/scheduled.flow.bpm"), Map.of()).orElseThrow();
}
```

fixed delay 可防止同一个 scheduler 实例重叠执行。分布式部署若只允许一次集群级 invocation，仍需外部单一所有者机制或幂等策略。

## 事实源

运行时支持边界由以下 provider 类决定：

- TBBPM parser registry: `TbbpmElementParserRegistry`
- TBBPM semantic frontend: `TbbpmSemanticFrontend`
- BPMN parser registry: `BpmnElementParserRegistry`
- BPMN semantic frontend: `BpmnSemanticFrontend`
- 普通 compiled realization: `JavaProcessCodeGenerator`
- Durable realization 边界: `DurableMachineLowerer`

只有 parser 并不代表可执行。BPMN `message` 和 loop characteristics 等元素是元数据或包装元素，不是独立运行时节点。Durable-only
语义使用 `DurableMachineLowerer`，而不是ProcessEngine runtime realization。新增节点支持时，应同时更新 parser、source validator、
semantic frontend、适用的 eligibility/lowering/realization、测试和本文档。
