# TBBPM 规范

对应 Schema 位于 `compileflow-tbbpm/src/main/resources/TBBPM.xsd`。运行时要求 Java 17 或更高版本。

## 1. 范围

TBBPM 是 CompileFlow 原生的紧凑 XML 流程格式。流程定义经过解析和语义校验后，可以通过以下两个执行入口之一运行：

- `ProcessEngine`：在应用进程内执行一次流程调用；
- `DurableProcessEngine`：在受支持的 Wait、Timer、Effect 与终态边界持久化 Run。

TBBPM 不提供内置人工任务生命周期，也不支持任意 BPMN 消息关联。`timerTask` 仅用于 Durable，`ProcessEngine` 会拒绝该节点。
Effect 是普通 Action 的执行语义，不是独立节点。对于普通引擎，`waitTask` 和 `waitEventTask` 是后续调用 `trigger(...)` 时使用的具名入口；
对于 Durable，它们表示带一次性 Wait 令牌的持久化 Run 边界。应用必须显式选择执行入口。

格式契约分为三层：

1. `TBBPM.xsd` 定义 XML 结构与基础属性类型。
2. TBBPM 解析器将节点和转移转换为模型。
3. 模型校验和严格流程预检检查流程图、循环、Action 及编译语义。

未知元素与非法属性会被拒绝，不会被静默忽略。

## 2. 文档结构

根元素为 `<bpm>`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpm code="greeting.flow" name="Greeting" description="Build a greeting">
    <var name="name" dataType="java.lang.String" inOutType="param"/>
    <var name="greeting" dataType="java.lang.String" inOutType="return"/>

    <start id="start" name="Start" g="0,0,32,32">
        <transition to="greet"/>
    </start>
    <scriptTask id="greet" name="Build greeting" g="80,0,120,48">
        <action type="script" language="qlexpress">
            <input source="name" target="name" dataType="java.lang.String"/>
            <output target="greeting" dataType="java.lang.String"/>
            <code>'Hello, ' + name</code>
        </action>
        <transition to="end"/>
    </scriptTask>
    <end id="end" name="End" g="240,0,32,32"/>
</bpm>
```

### 2.1 根属性

| 属性          | 必填 | 含义                                                          |
| ------------- | ---: | ------------------------------------------------------------- |
| `code`        |   是 | 流程编码。命名空间、版本和别名属于 `ProcessRef`，不写入 XML。 |
| `name`        |   否 | 显示名称。                                                    |
| `description` |   否 | 人类可读描述。                                                |

根元素不包含部署版本、租户、别名或触发能力模式标志。模型包含顶层
`waitTask` 或 `waitEventTask` 时即为可触发流程。XML 中的 `code` 必须与 `ProcessDefinition` 中的流程编码完全一致。
流程编码最长 128 个字符，以 ASCII 字母或数字开头，且只能包含 ASCII 字母、数字、`.`、`_`
或 `-`。

### 2.2 变量

```xml
<var name="orderId"
     dataType="java.lang.String"
     inOutType="param"
     defaultValue=""
     description="Order identifier"/>
```

| 属性           | 必填 | 含义                           |
| -------------- | ---: | ------------------------------ |
| `name`         |   是 | 生成的 Java 变量名。           |
| `dataType`     |   是 | Java 类型名。                  |
| `inOutType`    |   是 | `param`、`return` 或 `inner`。 |
| `defaultValue` |   否 | 初始值。                       |
| `description`  |   否 | 文档说明。                     |

三种方向的流程变量名必须全局唯一。根变量的 `name` 同时作为生成的 Java 字段名和流程上下文 Map 的键。
根变量属于声明，而不是映射；带方向的 `source` 与 `target` 属性只用于 Action 和流程调用。

变量方向同时规定数据所有权：`param` 是调用方提供的输入，`return` 是流程输出，`inner` 是流程内部状态。
`ProcessEngine.execute` 与 Durable 启动只接受由 `param` 组成的封闭 Map，可以省略部分参数；传入 `return`、`inner`
或未声明字段会直接失败。缺失的参数保留定义中的默认值，传入值为 `null` 的键表示显式设置为空。
普通 Trigger 入口使用调用方提供的状态启动一次新调用，可以接收任意已声明的根变量，但不会恢复 Durable 的后续执行位置。

## 3. 节点与转移

每个可执行或图形节点必须有 `id`，几何属性 `g` 可选。节点 ID 必须在完整模型中全局唯一，包括嵌套循环。除下文特别说明外，`name`
与 `description` 均为可选。

转移必须作为源节点的子元素：

```xml
<transition to="next" condition="amount &gt; 100" name="highValue"/>
```

`to` 必填；`condition`、`name` 和 `g` 可选。出向转移按声明顺序求值。TBBPM 不接受转移上的 `priority`、
根级转移或 `from` 属性。目标必须能在转移所属的节点容器内解析。每个可执行节点都必须能从该容器配置的
开始节点到达；仅用于图形展示的 `note` 不参与此可达性规则。

### 3.1 开始与结束

```xml
<start id="start" name="Start" g="0,0,32,32">
    <transition to="task"/>
</start>
<end id="end" name="End" g="240,0,32,32"/>
```

每个流程必须恰好有一个 `start` 和一个 `end`。开始节点必须恰好有一条出向转移；结束节点必须是唯一控制流出口且不能有出向转移。

### 3.2 任务

`autoTask` 与 `scriptTask` 必须执行且仅执行一个 `<action>` 后继续。`autoTask` 只接受应用提供的 Java 类或 Spring Bean Action；
`scriptTask` 只接受流程定义中声明的脚本 Action。脚本具有显式输入和可选输出，与 `INTERPRETED` 执行模式无关。
纯控制流应直接用转移和网关表达，不应创建空任务节点。

```xml
<autoTask id="load" name="Load order" g="80,0,120,48">
    <action type="java" execution="replayable" class="com.example.OrderService" method="load">
            <input target="orderId" dataType="java.lang.String"
                 source="orderId"/>
            <output dataType="com.example.Order"
                 target="order"/>

    </action>
    <transition to="end"/>
</autoTask>
```

Action 并发与 Durable 执行语义相互独立：

- `concurrency="forbidden|safe"` 表示是否允许兄弟分支同时调用；默认 `forbidden`。
- `execution="replayable|effect"` 是 `<action>` 属性；普通引擎可以省略，Durable 中的每个可执行 Action 必须显式声明。
  `replayable` 在流程片段中运行，事务未提交时可能重新执行；`effect` 会在发送外部调用前先提交 Effect 边界。

可选的 `<invocationPolicy>` 管理同步调用的重试和超时，见第 6 节。只有省略 `execution` 或声明为 `replayable`，
且实现能够承受重试和超时不确定性时，才能使用该策略；`effect` Action 禁止声明它。普通引擎只同步执行一次 Effect，
不提供故障恢复；Durable 则将同一 Action 保存为由 `<effectPolicy>` 管理的 Effect。

### 3.3 Wait 入口

`waitTask` 与 `waitEventTask` 是按节点 ID 选择的 Trigger 入口：

```xml
<waitTask id="approval" name="Approval" timeout="PT24H" g="80,0,120,48">
    <transition to="end"/>
</waitTask>

<waitEventTask id="payment" name="Payment"
               event="payment.completed" timeout="PT24H" g="80,80,120,48">
    <transition to="end"/>
</waitEventTask>
```

两个节点都使用全局唯一且非空的节点 `id` 作为 Trigger 选择条件，并可声明非负 `timeout`。`waitEventTask`
还要求 `event`；`waitTask` 没有 event 属性。

`ProcessEngine` 执行到 Wait 入口时，当前调用结束。之后调用 `trigger(...)` 会从匹配的入口节点 ID 启动一次新执行，其输入必须携带
剩余流程需要的全部状态。对于 `waitEventTask`，请求 event 必须匹配。Wait 节点没有 `inAction` 或 `outAction`；入口前后
需要工作时使用显式 task 节点。

在 `ProcessEngine` 中，这只是调用入口，不表示恢复持久化实例或关联消息。Wait 入口只能位于顶层；普通引擎没有定时调度能力，
因此会拒绝带 `timeout` 的 Wait。Durable 到达该节点时会提交一个持久化 Wait；
`DurableProcessEngine.completeWait(...)` 使用 `WAIT_COMMITTED` 事件提供的一次性令牌确定对应的 Wait，之后由 Durable Worker
继续推进重新满足执行条件的 Run。Durable Wait 可以位于循环内，但令牌只对创建它的那次循环迭代有效。

### 3.4 Durable Timer 与 Effect Action

`timerTask` 把 Durable Run 挂起到到期时间，并由数据库权威时钟判断是否到期：

```xml
<timerTask id="cooldown" name="冷却" duration="PT15M" g="80,0,120,48">
    <transition to="charge"/>
</timerTask>
```

必须且只能提供一个调度属性：

| 属性                 | 契约                                                       |
| -------------------- | ---------------------------------------------------------- |
| `duration`           | 非负 ISO-8601 `java.time.Duration`，例如 `PT15M`。         |
| `durationExpression` | 基于持久化流程状态求值的确定性表达式，结果为时长。         |
| `wakeAtExpression`   | 基于持久化流程状态求值的确定性表达式，结果为绝对唤醒时间。 |

相对时长以 Store 调度时刻为基准。`wakeAtExpression` 计算出的绝对时间会原样保留；即使该时间早于调度时刻，
也只表示下次 Timer 扫描时可以处理，不表示启动 Run 的调用会同步完成 Timer。
实际处理时间不得早于调度时刻或到期时间。

普通任务可以通过其 Action 声明 Durable Effect 边界：

```xml
<autoTask id="charge" name="扣款" g="240,0,120,48">
    <action type="spring-bean" execution="effect" bean="paymentAction"
                      class="com.example.PaymentAction" method="charge">
            <input target="orderId" dataType="java.lang.String"
                 source="orderId"/>
            <input target="effectId" dataType="java.lang.String"
                 source="__cf_effect_id"/>
            <output dataType="java.lang.String"
                 target="receiptId"/>

        <effectPolicy recovery="retry"
                      maxAttempts="3"
                      recoveryDelay="PT5S"/>
    </action>
    <transition to="end"/>
</autoTask>
```

任务与 Action 仍属于业务模型，不会产生另一套 Effect 目标身份。当前部署负责解析并调用 Action 实现。Kernel 在发送外部调用前提交
Effect 及可移植输入，将稳定的 occurrence ID 作为调用键，记录结果后再从同一任务继续执行。输入映射必须指定流程变量来源或默认值；
输出映射必须指向已声明的根流程变量。

省略 `<effectPolicy>` 时使用保守的 `manual` 恢复模式：只发送一次外部调用，结果未知时等待人工处理。显式恢复模式包括
`manual`、`retry`（使用同一 Effect ID 重新发送）和 `reconcile`（先由专用的 `reconcileAction` 核对操作是否已完成、未执行或仍未知）。
业务调用需要时，`retry` 和 `reconcile` 可以将稳定的 `__cf_effect_id` 映射到 Action 输入，但该映射本身不能保证幂等。
策略只规定尝试次数、恢复间隔和最长恢复时间等流程语义；轮询、租约和 Provider 缺失时的退避属于运行时职责。

`reconcileAction` 只包含调用定义和零个或多个输入。每个输入的 `source` 表示原始 Effect 请求中已经持久化的字段，
`target` 表示核对操作的调用参数；`source` 不是流程表达式。除此之外只允许稳定的 Kernel 元数据字段 `__cf_effect_id`。
通过该字段注入的 Action 输入参数不属于持久化请求字段。尝试次数属于运行时遥测信息，不能映射为 Action 或核对操作的业务参数。
默认值、输出、执行模式、调用策略和嵌套 Effect 策略均为非法。核对操作返回 `EffectReconcileOutcome`；
确认成功后的结果仍通过原始 Effect Action 的输出声明写回流程状态。

在结构化集合中，如果每次 Effect 需要使用不同的恢复方案，`effectPolicy` 可以改为声明
`recoveryPlanVariable="variableName"`。该可见变量必须是
`com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan` 类型；此时禁止同时声明静态恢复属性。
状态机在创建 Effect 前读取并校验该值，Store 会随 Effect 一并固定。若该值可能选择 `RECONCILE`，
策略中仍须声明唯一且静态的 `reconcileAction`。

Timer 与 Effect 边界都可以位于循环中。`ProcessEngine` 遇到 `timerTask` 或带期限的 Wait 时会直接失败；Effect Action
只同步调用一次，不提供 Durable 恢复。无期限的顶层 Wait 仍是供后续独立调用使用的 Trigger 入口。
注册和运行方式见 [Durable 使用指南](../durable-process.md)。

### 3.5 网关

TBBPM 支持：

- `exclusive`：排他分支或汇合。
- `parallel`：并行分支或汇合。
- `inclusive`：执行所有匹配的出向分支。

```xml
<exclusive id="route" name="Route" g="80,0,80,48">
    <transition to="premium" condition="amount &gt;= 1000"/>
    <transition to="normal"/>
</exclusive>
```

排他或包容分支最多有一条默认转移，即不含 `condition` 的出向转移。并行转移不能带条件。所有条件表达式必须能被 javac 静态判定为
`boolean` 或 `java.lang.Boolean`；只有
`true` 匹配，`false` 与可空 `Boolean` 的 `null` 都不匹配。该真值契约同样适用于
`while` 的 condition 以及条件型 `break`/`continue`。条件表达式必须无副作用：不得修改
流程变量、调用有副作用的服务或依赖求值次数；引擎可按模型顺序求值，但不把求值本身视为可提交的流程动作。

同一种网关元素同时表示 split 和 join，角色完全由拓扑推导，不使用 `next`、配对 ID 或额外 fork/join 节点类型：

- split 必须是 1 条入边、2 条及以上出边；
- join 必须是 2 条及以上入边、1 条出边；
- 同时多入多出的 mixed gateway 会被拒绝，必须拆成 join 后接 split；
- `parallel`/`inclusive` split 的共享后续必须经同类型显式 join，或所有分支直接结束；
- `exclusive` 只产生一个 token，因此允许在普通节点隐式汇合，也可使用同类型显式 join；
- 所有可执行节点必须可达，整个容器必须只有一个控制流出口；交叉或部分汇合的非结构化 region 会在生成代码前被拒绝。

网关是纯路由与同步元素，不能包含 action。路由前或同步后需要执行的计算与副作用必须建模为
独立任务，使执行、重试、幂等与可观测性的归属始终落在一个明确的业务步骤上。汇合网关的唯一出向转移也不能带 `condition`
，因为它不执行节点体或路由判断；需要条件路由时必须在 join 后增加独立 split。校验会拒绝不符合此契约的定义，不会静默跳过
action 或条件。

Action、script、expression 与被调流程只能只读借用 Process 状态可达的全部对象。Process 状态变化只能通过显式 output
或明确的 Process 构造表达。Component resolver 和 script provider 可能被不同分支或不同 Process invocation 并发调用，
实例隔离或线程安全由其生命周期 owner 负责。分支失败无法回滚外部副作用，因此外部动作仍必须使用匹配的 Effect 协议。

进入并发 region 时，`inclusive` 会先在父流程状态上按模型顺序计算全部条件，再启动激活分支。
每个激活分支运行在独立的强类型分支帧上；分支内的后续节点可以读取本分支此前的写入，兄弟分支
不可见。所有激活分支成功后，父线程才按模型序号提交各分支声明的根变量写集；任一分支失败、取消或分支任务提交被拒绝都不会发布任何分支流程变量。并发
return mapping 必须直接指向根流程变量，
`order.status`、集合元素或数组元素等嵌套写入会被拒绝。该保证只覆盖 JVM 内父流程变量发布，不回滚已经发生的数据库、消息或 RPC
副作用。

分支帧复制强类型字段值而不深拷贝任意对象图，因此所有可达对象引用仍是只读的。分析器只拒绝不同分支写同一个根 Process
变量；一个分支读取而另一个分支显式写入时，读取看到 fork 时快照，不构成冲突。可变 List、Map、数组、实体或普通 POJO
不得被原地修改。需要聚合时，应让分支写不同的中间变量，在 join 后串行计算最终值，不依赖完成顺序、共享集合或
last-write-wins。

### 3.6 内嵌 BPM 与 BPM 调用

```xml
<subBpm id="validation" name="Validation" g="80,0,220,120">
    <start id="validationStart">
        <transition to="validate"/>
    </start>
    <autoTask id="validate">
        <!-- action -->
        <transition to="validationEnd"/>
    </autoTask>
    <end id="validationEnd"/>
    <transition to="next"/>
</subBpm>
```

`subBpm` 表示当前流程内部的嵌套作用域。它没有独立的流程身份、资源位置、版本、调用点绑定或参数映射，
直接共享外层流程状态。它必须恰好包含一个直接 `start` 和一个直接
`end`，所有直接可执行子节点都必须从该 start 可达；允许嵌套 `subBpm`、`while` 与 `foreach`。语义前端将它
降低为与 BPMN `subProcess` 相同的作用域边界，不会创建子流程调用。位于循环内时，它也可以包含 `break` 与
`continue`；这些控制节点绑定最近的外层循环，而不是 `subBpm` 本身。

```xml
<bpmCall id="validate" name="Validate" code="order.validate"
         classpath="flows/order-validate.bpm" g="80,0,120,48">
    <input target="orderId" source="orderId"/>
    <output source="validatedOrder" target="validatedOrder"/>
    <transition to="end"/>
</bpmCall>
```

`bpmCall` 调用另一个流程定义。`code` 必填，并遵循与根流程定义相同的流程编码规则；
`classpath` 与 `version` 必须且只能提供一个。`classpath` 是应用类路径中精确、规范化的资源路径，不是 URI，
也不支持协议前缀、父目录跳转、通配符或相对于调用方解析。
在 `ProcessEngine` 中，被调流程同步执行；
被调流程变量负责调用方上下文的输入输出映射，因此只支持 `param` 与 `return`。若 `ProcessEngine` 调用方需要在调用结束后暂停，
应显式连接 `waitTask` 或 `waitEventTask`；流程调用与外部事件关联是两种独立语义。

调用输入只声明 `source` 或 `defaultValue` 之一以及被调流程的 `target`；精确被调流程的 `param` 声明是其类型的唯一事实源。
调用输出以 `source` 声明被调流程的 `return` 变量，以 `target` 声明调用方已定义的变量。Process Call 映射不声明
`dataType`。

直接提供的流程定义可以调用确定的类路径定义，也可以调用已加载的确定版本。确定版本的流程定义只能调用确定版本，
已发布制品同样只允许依赖确定版本；版本目标继承调用方的命名空间。别名只用于根流程准入，
不向嵌套调用继承。整个传递调用图必须在第一个业务动作前解析并持有；运行时按确定的调用点绑定执行，不再按流程编码解析。

在 `durable-strict@1` 中，流程调用会在同一个流程实例内压入确定的被调流程调用帧，不会创建可独立寻址的新流程实例。
`Continuation` 持久化确定的流程目标和调用点绑定，恢复时继续使用已经持有的绑定，不会重新进行别名路由。
调用成功后应用已声明的返回值映射；调用失败会成为调用方流程的明确失败。由于无法证明被调流程的写入集合与外部操作只影响所在分支，
`bpmCall` 仍禁止位于并发区域。

### 3.7 循环

TBBPM 用两个结构化节点表达两种不同意图：`while` 表示按条件重复，`foreach` 表示遍历集合；不存在通用循环节点或
类型判别属性。每个循环体必须恰好包含一个直接 `start` 和一个直接 `end`，所有直接可执行子节点必须从 start 可达，end 不得有出边。
循环节点自身的 `transition` 在循环结束后离开容器。

条件循环：

```xml
<while id="retryLoop" name="Retry"
           condition="attempt &lt; maxAttempts"
           maxIterations="10" index="attempt" g="80,0,180,140">
    <start id="retryStart"><transition to="try"/></start>
    <autoTask id="try" name="Try">
        <action type="spring-bean" bean="retryService" class="com.example.RetryService" method="tryOnce"/>
        <transition to="retryEnd"/>
    </autoTask>
    <end id="retryEnd"/>
    <transition to="end"/>
</while>
```

`condition` 和安全上限 `maxIterations`（`1..2147483647`）都是必填项。`index` 是可选的、从零开始且仅在循环体内可见的词法计数器。
达到上限后条件若仍为真，执行失败，不会静默截断。该上限是 TBBPM 源语言要求，而不是 Durable 调度器限制。

集合遍历：

```xml
<foreach id="itemsLoop" name="Items" collection="items"
             item="item" itemType="com.example.Item"
             index="index" g="80,0,180,140">
    <start id="itemsStart"><transition to="handle"/></start>
    <autoTask id="handle" name="Handle">
        <action type="spring-bean" bean="itemService" class="com.example.ItemService" method="handle">
                <input target="item" dataType="com.example.Item"
                     source="item"/>

        </action>
        <transition to="itemsEnd"/>
    </autoTask>
    <end id="itemsEnd"/>
    <transition to="end"/>
</foreach>
```

`collection`、`item` 和 `itemType` 必填；`index` 可选；`execution` 可取默认的 `sequential` 或仅 Durable
支持的 `parallel`。item 和 index 都是词法局部变量，必须是互不相同的合法 Java 标识符，且不能遮蔽流程变量或外层循环局部变量。
`collection` 必须引用声明为 `Iterable` 或数组的变量，`itemType` 必须是合法 Java 类名。集合声明恰好一个直接类型参数时，该参数必须与
`itemType` 兼容；其他集合声明仍然合法，运行时会逐一校验快照元素是否符合 `itemType`。并行执行会进一步把输入约束为兼容
`java.util.List` 的声明，以保证恢复前后的输入索引稳定。

可选的原子 `output` 子元素用于声明按索引聚合结果：

```xml
<foreach id="itemsLoop" collection="items" item="item"
             itemType="com.example.Item">
    <output target="results" source="itemResult"/>
    <start id="itemsStart"><transition to="handle"/></start>
    <autoTask id="handle"><!-- effect action --><transition to="itemsEnd"/></autoTask>
    <end id="itemsEnd"/>
    <transition to="end"/>
</foreach>
```

存在 `output` 时，它必须是 `foreach` 的第一个直接子元素；两个属性都必填，并分别引用不同的已声明流程变量。
`target` 必须声明为 `java.util.List`，不能声明为具体 List 实现；聚合只保证有序 List，不保证具体实现或可变性。
`source` 必须引用 `inner` 流程变量；target 声明了类型参数时，该参数必须与 source 变量类型
完全一致，raw output list 仍然合法。并行聚合始终按输入索引排序，不依赖完成顺序；顺序聚合按迭代顺序收集，并仅在循环退出时原子发布，
触发 `break` 的当前迭代结果也会被收集。每次迭代开始前，输出 source 变量都会重置为其流程变量声明的默认值，因此提前 `continue` 不会复用
上一次迭代的结果。空输入直接产生空输出集合，不创建迭代任务。

`break` 与 `continue` 可以出现在顺序循环体内，并可带条件表达式。并行 `foreach` 中仍允许每个迭代独立 `continue`，但禁止
`break`，因为单次迭代无法确定性地取消已发出的兄弟迭代。顺序循环可以嵌套；并行集合循环可以位于顺序循环内，但已处于并发作用域时
不能再嵌套并行循环或并发网关。`break` 与 `continue` 也可以位于循环内嵌套的 `subBpm` 作用域中，并始终绑定最近的外层循环；
因此嵌套循环拥有自己的控制节点。`ProcessEngine` 的触发入口与 Parallel/Inclusive 并发分支不能作为循环子节点。Durable 顺序循环
可包含等待、定时器、外部操作以及结构化 Parallel/Inclusive 作用域。所有并发作用域内仍禁止流程调用。循环及控制表达式继续使用
生成 Java 表达式的契约。

并行任务使用有界滚动窗口，由 `compileflow.durable.worker.max-active-iterations` 控制（`1..64`，默认 `32`）。
该窗口属于运行时调度策略，不会持久化为流程身份。输入只冻结一次；各次迭代的状态彼此隔离；崩溃恢复后
索引身份保持稳定；配置输出聚合时，目标变量始终按输入索引而不是完成顺序汇聚。`Continuation` 只保存一份
父状态和每个活动迭代的状态差量。空输入不会创建迭代任务；配置输出时会生成空的目标集合。

### 3.8 注释

`note` 是不可执行的图形元数据，不能参与转移：

```xml
<note id="note1" name="Context" comment="Handled by the application"
      g="80,80,180,40"/>
```

## 4. 动作

所有动作使用同一种规范 XML 形态：

```xml
<action type="java" class="com.example.Handler" method="run"/>
```

内置动作类型为：

| `type`        | 动作必需内容                                                       | 含义                                 |
| ------------- | ------------------------------------------------------------------ | ------------------------------------ |
| `java`        | `class`，可选 `method`（默认 `execute`）及 `input`/`output` 子元素 | 调用应用提供、可复用的 Java 能力。   |
| `spring-bean` | `bean`、`class`，可选 `method`（默认 `execute`）及映射             | 调用应用提供的 Spring 能力。         |
| `script`      | `language`、一个 `code`、可选映射                                  | 通过具名脚本执行器运行流程中的代码。 |

`script` 是动态代码唯一的协议动作类型。其 `language` 选择已注册的 `ScriptExecutor`。QL 与 Java 共用
同一种 XML 结构和语义计划；新增语言也必须遵守该契约。Exclusive、while、timer 与转移守卫条件仍生成 Java 源码，
不通过脚本执行器。

每个脚本输入和输出都用 `<input>` 或 `<output>` 显式声明。Java Script 源码是一段方法体，例如
`return price.multiply(quantity);`。加载流程运行时时，内置 Java 执行器会生成带类型的包装代码，并以
`javac --release 17` 编译；得到的 `ScriptProgram` 只属于该确定且可丢弃的运行时，绝不持久化。脚本语言、精确源码与声明签名
仍是不可变流程版本的事实来源，因此运行时始终可以根据源码重新生成。当前规范只接受 JDK 平台的输入和输出类型。
Java Script 是可信的嵌入式计算，不是安全沙箱；面向不可信用户的 Workbench 必须使用默认禁止联网的隔离代码运行器。

脚本示例：

```xml
<action type="script" language="qlexpress">
    <input source="price" target="price" dataType="java.math.BigDecimal"/>
    <input source="quantity" target="quantity" dataType="java.lang.Integer"/>
    <output target="total" dataType="java.math.BigDecimal"/>
    <code>price * quantity</code>
</action>
```

Java Code 示例：

core 提供可信进程内 Java executor；QL 与 Java 默认均可用。

```xml
<action type="script" language="java">
        <code><![CDATA[return java.time.Instant.now().toString();]]></code>

</action>
```

## 5. 动作映射

所有映射只使用一个方向代数：读取 `source`，写入 `target`。

Action 输入写作 `<input source="processExpression" target="argument" dataType="java.lang.String"/>`。
`target` 与 `dataType` 必填，`source` 与 `defaultValue` 必须且只能声明一个。`source` 表达式按原文保留；
`target` 是 action 局部参数名或脚本 binding。

Action 输出写作 `<output target="processVariable" dataType="java.lang.String"/>`。其 source 是 action 的唯一
返回值，因此由上下文隐含。两个属性均必填，最多只能有一个输出，且 target 必须是已声明的根流程变量；省略输出即丢弃返回值。

流程调用沿用同一代数，但两端都显式声明：调用输入把调用方 `source` 映射到被调流程 `target`；调用输出把被调流程
`source` 映射到调用方 `target`。调用映射不重复 `dataType`，被调流程的 `param` 与 `return` 声明是类型事实来源。

## 6. 调用策略

```xml
<action type="java" class="com.example.OrderService" method="submit">
  <invocationPolicy timeout="PT2M"
           attemptTimeout="PT30S"
           maxAttempts="3"
           initialBackoff="PT1S"
           backoffMultiplier="2.0"
           maxBackoff="PT10S"
           jitter="full"
           retryOn="transient"
           onFailure="propagate"/>
</action>
```

Duration 使用规范大写 ISO-8601 形式，并保持整毫秒精度。`timeout` 是整个逻辑调用的正 wall-clock 预算，
包含所有尝试和重试退避；`attemptTimeout` 是单次尝试的正预算，同时声明时不得大于 `timeout`。每次尝试
实际获得单次预算与剩余总预算中的较小值。`maxAttempts` 范围为 1 到 100，并包含首次调用；backoff multiplier 不小于 1.0。
`jitter` 可取 `full`（默认）或 `none`；全抖动会在 0 到当前指数退避上限之间均匀分散每次等待。
单次尝试超时可按 `retryOn` 重试；总超时永不重试，直接交给 `onFailure`。总 deadline 之后不得启动新尝试，
退避等待计入总预算。取消是协作式的，不能撤销外部副作用。`retryOn` 与 `onFailure` 使用普通 Engine 中配置的
精确 lowercase kebab-case 名称。超时或重试不会把 Action 变成 Durable Effect，也不能证明外部系统的
exactly-once。作者只能为语义能够承受所选 invocation policy 的实现启用它。Durable 会拒绝非默认 invocation policy，不会静默忽略；它使用
`execution="replayable|effect"` 与可选 `<effectPolicy>`。

## 7. 校验与执行

`ProcessPreflightOptions.fast()` 会解析精确来源、校验 schema 并应用模型语义，包括：

- 缺失或重复的节点 ID 与流程变量。
- 缺失的 start/end 或非法网关形态。
- 无法解析的 transition，以及无法从容器开始节点到达的可执行节点。
- 缺失的循环属性、非法迭代上限、畸形循环作用域及错误的 `break`/`continue` 位置。
- ProcessEngine 执行的循环内嵌触发入口；Durable Parallel `foreach` 内再次嵌套并发 split。Durable 顺序循环可以包含结构化并发 split。

`ProcessPreflightOptions.strict()` 还会构建并编译 runtime，因此能够校验生成的 Java、已注册脚本 executor、Java 类型及方法相关语法。

执行会解析一个精确的 definition byte snapshot，并以该内容和本地编译输入建立 runtime identity。
随后执行所选 runtime（默认 `COMPILED`，或基于同一语义计划的 `INTERPRETED`）。重复请求可以复用该精确
runtime。Trigger 执行与 ProcessEngine 执行使用相同的 source、routing、runtime、result 与 observability 管线。

```java
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "payment.flow", "flows/payment.flow.bpm"),
        ProcessTrigger.on("payment", "payment.completed"),
        Map.of("paymentId", "P-42"));
```

该调用从 `payment` 入口启动新 invocation，不会恢复此前的 Java 对象或已存储流程实例。

## 8. 一致性要求

符合本规范的 CompileFlow TBBPM 实现：

- 只接受当前 XSD 与语义校验器允许的 XML。
- 拒绝未知元素、非法属性、无法解析的转移和不支持的 action。
- 仅使用 `foreach` 与 `while` 两种循环节点，不使用通用循环判别属性。
- ProcessEngine trigger 入口只允许位于顶层；Durable Wait 可位于支持的作用域和循环中。
- 以 Java 17 release 编译生成代码。
- `ProcessEngine` 调用不持久化后续执行状态；只有显式使用 Durable 执行面时才会恢复流程实例。
- 不提供通用消息关联或 Engine 内建人工任务生命周期。

## 9. 相关文档

- [流程模型架构](../architecture/process-model.md)
- [流程格式参考](process-formats.md)
- [Java API 参考](../api-reference.md)
- [节点支持](../node-support.md)
