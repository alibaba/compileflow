# CompileFlow BPMN 扩展规范

本规范定义 CompileFlow 在受支持 BPMN 2.0 可执行子集中拥有的 XML 词汇，涵盖扩展语法和执行约束。标准 BPMN
元素的支持范围单独记录在[节点支持列表](../zh/node-support.md)中。

## 1. Namespace 与校验

CompileFlow 扩展使用以下 namespace：

```xml
xmlns:cf="http://www.compileflow.org"
```

严格解析会校验两份随模块发布的 schema：

- [`BPMN20.xsd`](../../compileflow-bpmn/src/main/resources/BPMN20.xsd) 校验标准 BPMN 结构；
- [`CompileFlowBpmnExtensions.xsd`](../../compileflow-bpmn/src/main/resources/CompileFlowBpmnExtensions.xsd)
  校验 CompileFlow 扩展元素、属性和封闭值域。

Schema 无法表达扩展所属 BPMN 元素以及全部代码生成约束。因此 schema 校验后，BPMN parser 与 model validator
继续执行本规范中的归属和语义校验。未知扩展 namespace、未知 `cf:` 元素、不支持的属性、重复 singleton
扩展以及叶子扩展中的子元素都会被拒绝，不会被忽略。

Engine 从自身 classpath 解析 schema，并禁用外部 DTD 与 schema 访问。BPMN 文件运行时不需要
`xsi:schemaLocation` 提示。

## 2. 扩展归属

所有子扩展都放在对应 BPMN 元素的 `extensionElements` 中。

| 扩展                  | BPMN owner     |       数量 | 用途                           |
| --------------------- | -------------- | ---------: | ------------------------------ |
| `cf:var`              | `process`      | 零个或多个 | 声明流程变量。                 |
| `cf:input`/`output`   | `scriptTask`   | 零个或多个 | 映射脚本输入和可选返回值。     |
| `cf:input`/`output`   | `callActivity` | 零个或多个 | 映射被调用流程的输入和输出。   |
| `cf:action`           | `serviceTask`  |   恰好一个 | 声明任务实现。                 |
| `cf:invocationPolicy` | `cf:action` 或 `scriptTask` | 零个或一个 | 管理同步超时、重试和最终失败。 |
| `cf:effectPolicy`     | `cf:action` 或 `scriptTask` | 零个或一个 | 管理 Effect 恢复。             |

限定属性 `cf:collection`、`cf:item`、`cf:itemType`、`cf:index`、
`cf:target` 和 `cf:source` 直接属于 `multiInstanceLoopCharacteristics`，不是子扩展元素。
`cf:execution` 直接属于 `scriptTask`。

扩展出现在其他 owner 上即为非法。`serviceTask` 不能是空任务，preflight 要求它恰好包含一个
`cf:action`。

## 3. 变量

```xml
<extensionElements>
  <cf:var name="orderId"
          dataType="java.lang.String"
          inOutType="param"/>
</extensionElements>
```

| 属性           | 必填 | 含义                           |
| -------------- | ---: | ------------------------------ |
| `name`         |   是 | 生成的 Java 变量名。           |
| `dataType`     |   是 | Java 类型名。                  |
| `inOutType`    |   是 | `param`、`return` 或 `inner`。 |
| `defaultValue` |   否 | 初始值。                       |
| `id`           |   否 | XML identity 元数据。          |
| `description`  |   否 | 说明文字。                     |

流程变量名必须唯一，同时作为生成字段和 context map key。根变量是声明而不是映射；方向明确的 `source` 与 `target`
属性只存在于 action 和流程调用边界。

根变量方向是可执行的所有权契约：`param` 是调用方拥有的 admission 输入，`return` 是 Process 拥有的输出，`inner` 是
Process 拥有的内部状态。普通 `ProcessEngine.execute` 与 Durable Start 只接受由 `param` 构成的封闭、部分 Map；
`return`、`inner` 和未声明字段会失败，不会被静默忽略。普通 trigger-entry invocation 是独立的状态 seed API，可以接收
任意已声明根变量；它不是 Durable continuation 恢复。

Action 输入读取 `source` 并写入 action 局部 `target`，且 `source` 与 `defaultValue` 必须且只能声明一个。Action
输出的 source 是隐含的 action 返回值，只显式声明 `target`。流程调用两端都显式声明：输入把调用方 `source` 映射到
被调流程 `target`，输出把被调流程 `source` 映射到调用方 `target`。

## 4. Action

`serviceTask` 使用统一结构：

```xml
<cf:action type="spring-bean" execution="replayable" bean="orderService"
                   class="com.example.OrderService"
                   method="submit">
    <cf:input target="orderId"
            dataType="java.lang.String"
            source="orderId"/>

  <cf:invocationPolicy attemptTimeout="PT30S" maxAttempts="3"/>
</cf:action>
```

每个 `serviceTask` 必须恰好包含一个 `cf:action`。Service Task 调用应用拥有的能力，其 `type` 只能是
`java` 或 `spring-bean`。

| `type`        | 实现契约                                                   |
| ------------- | ---------------------------------------------------------- |
| `java`        | `class`、可选 `method` 和可选 `cf:input`/`cf:output` 映射。 |
| `spring-bean` | `bean`、`class`、可选 `method` 和可选输入/输出映射。        |

`cf:code` 只用于 Effect 恢复策略中 script 类型的 `cf:reconcileAction`，不属于 `serviceTask` 的实现结构。

`execution` 可取 `replayable` 或 `effect`，记录与 TBBPM 相同的 Action-level Durable 语义。`ProcessRuntime`
同步执行不解释它；Durable 要求显式声明，并 lower 为 replayable step 或受治理 Effect 边界。Process 状态可达的所有值都只能
只读借用；状态变化使用显式 output，component/provider 生命周期负责并发调用安全。

## 5. 原生 Script Task

BPMN `scriptTask` 使用标准 BPMN 字段描述实现，CompileFlow 扩展只负责变量映射：

```xml
<scriptTask id="calculate" scriptFormat="qlexpress">
  <extensionElements>
    <cf:input target="price" dataType="java.math.BigDecimal"
            source="price"/>
    <cf:output dataType="java.math.BigDecimal"
            target="total"/>
  </extensionElements>
  <script>price.multiply(new java.math.BigDecimal("1.20"))</script>
</scriptTask>
```

`scriptFormat` 和 `script` 都必须非空。Script Task 拥有流程定义内的源码；`scriptFormat` 选择已注册的
`ScriptExecutor`。Java 与 QL 共用这一标准 BPMN shape 和同一份 semantic plan。每个脚本输入与输出都以
`cf:input` 或 `cf:output` 显式声明。

Java Script source 是 method body。first-party executor 在 Process runtime load 时生成带类型的 wrapper，并以
`javac --release 17` 编译；`ScriptProgram` 只属于该精确、可丢弃的 runtime，绝不是持久化 Process identity。精确的
language、source 与声明签名始终绑定到不可变 Process Version，因此 runtime 可从 source 再次 prepare。V1 只接受 JDK platform
input/output type。Java Script 是可信嵌入式计算，不是 sandbox；不可信 Workbench 作者必须使用隔离 Code Runner。

CompileFlow 执行控制直接附着在 Script Task 上：`cf:execution` 是带命名空间的任务属性，
`cf:invocationPolicy` 与 `cf:effectPolicy` 是 `extensionElements` 的直接子元素。`cf:execution` 默认为
`replayable`；有副作用的脚本声明 `cf:execution="effect"`。`serviceTask` 不得包含 script action。

## 6. Invocation Policy

```xml
<cf:action type="java" class="com.example.OrderService" method="submit">
  <cf:invocationPolicy timeout="PT2M"
                 attemptTimeout="PT30S"
                 maxAttempts="3"
                 initialBackoff="PT1S"
                 backoffMultiplier="2.0"
                 maxBackoff="PT10S"
                 jitter="full"
                 retryOn="transient"
                 onFailure="propagate"/>
</cf:action>
```

| 属性                     | 默认值                       | 约束                                                        |
| ------------------------ | ---------------------------- | ----------------------------------------------------------- |
| `timeout`                | 不超时                       | 整个调用的正 ISO-8601 duration。                            |
| `attemptTimeout`         | 不超时                       | 单次尝试的正 ISO-8601 duration；不得大于 `timeout`。        |
| `maxAttempts`            | `1`                          | `1` 到 `100` 的整数，包含首次调用。                         |
| `initialBackoff`   | 重试时为 `PT1S`              | 非负 ISO-8601 duration，精确到整毫秒。                      |
| `backoffMultiplier` | `1.0`                        | 不小于 `1.0` 的有限数值。                                   |
| `maxBackoff`       | `100 * initialBackoff` | 非负 ISO-8601 duration，精确到整毫秒。                      |
| `jitter`            | `full`                       | `full` 或 `none`。                                          |
| `retryOn`                | `always`                     | `never`、`transient`、`always` 或已注册 retry policy 名称。 |
| `onFailure`              | `propagate`                  | `propagate`、`continue` 或已注册 failure policy 名称。      |

`timeout` 包含全部尝试与重试退避。每次尝试取 `attemptTimeout` 和剩余总预算中的较小值；单次尝试超时可以重试，
总超时永不重试并直接交给 `onFailure`，且总 deadline 之后不再开始新尝试。超时取消采用协作式语义，无法回滚外部副作用。
Policy 只管理一次ProcessEngine runtime 的同步 Action invocation，不是 Durable job 或 Effect
policy；作者只能把它用于省略 execution 或 `replayable` 且语义能够承受 retry 或 timeout 不确定性的实现。
`effect` Action 禁止声明 `cf:invocationPolicy`；Local 只同步调用一次，Durable 则物化为已提交 Effect occurrence。

## 7. Durable Effect Policy

带 `execution="effect"` 的 Action 可以在 `cf:action` 后包含一个 `cf:effectPolicy`。省略时表示一次不确定尝试后立即进入人工 review；manual 不声明自动恢复参数。
`recovery="retry"` 要求 `maxAttempts`（`2..100`）和不超过 24 小时的正 `recoveryDelay`；
`recovery="reconcile"` 要求 `maxAttempts`（`1..100`）、`maxReconcileAttempts`（`1..1000`）、正 `recoveryDelay`
以及恰好一个 `cf:reconcileAction`。Retry/reconcile 可选的 `maxRecoveryDuration` 必须为正且不超过 30 天。
Reconcile Action 是 recovery query adapter，不拥有 output、default value、execution、invocation policy 或嵌套 Effect
policy。每个 input 的 `source` 命名原始 Effect 请求中已经持久化的字段，`target` 命名 reconcile invocation 参数；
`source` 不是 Process expression。除此之外只允许稳定的 Kernel 元数据字段 `__cf_effect_id`。由该元数据字段注入的
Action input target 本身不属于持久化请求字段。Attempt number 属于 Runtime telemetry，不能映射为 Action 或
reconcile 的业务参数。返回值是 `EffectReconcileOutcome`，确认结果通过原始 Effect Action 的 output 写回。

对于结构化集合中的逐 occurrence 恢复边界，`cf:effectPolicy` 可改用
`recoveryPlanVariable="变量名"`。该可见变量必须声明为
`com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan`，且不得同时声明静态 recovery 属性。
Machine 在签发 Effect occurrence 时校验并冻结所选闭合值；若可能选择 `RECONCILE`，仍须提供静态
`cf:reconcileAction`。

## 8. 被调用流程

```xml
<callActivity id="price" calledElement="pricing.calculate"
              cf:version="v3">
  <extensionElements>
    <cf:input target="request" source="request"/>
    <cf:output source="price" target="price"/>
  </extensionElements>
</callActivity>
```

`calledElement` 是被调用流程的规范 code，`cf:classpath` 与 `cf:version` 必须且只能提供一个。`cf:classpath` 是
应用 classpath 中精确、规范化的资源路径，不是 URI，也不支持 scheme、父目录跳转、通配符或相对调用方解析。
Direct definition 可以调用 Classpath 或精确 Version；精确 Version definition 与
published artifact 只能调用 caller namespace 下被调流程的精确 Version。Alias 只用于 root admission。调用图在业务执行前
完成解析；Durable 恢复持久化精确流程目标与 call-site identity，不重读当前 source 或 route。
Durable 把该调用作为同一 Run 内的另一个 `ProcessInvocation` frame 执行，不创建可独立寻址的 Run。精确被调流程的 `param`
与 `return` 声明是类型和方向的唯一事实源，因此被调流程映射不声明 `dataType`。输入必须且只能选择 `source` 或
`defaultValue` 之一；输出必须同时声明被调流程 `source` 与调用方 `target`。

## 9. 多实例属性

```xml
<multiInstanceLoopCharacteristics isSequential="true"
    cf:collection="items"
    cf:item="item"
    cf:itemType="com.example.Item"
    cf:index="index"/>
```

两个执行面都支持顺序多实例；并行多实例仅支持 Durable，ProcessEngine 执行会拒绝。`cf:collection` 和 `cf:item` 必填且必须是合法 Java
标识符。Collection 必须引用已声明流程变量或外层多实例变量。
`cf:index` 可选；iteration-local 名称不能与流程变量或外层词法变量冲突。`cf:itemType` 如存在，必须是 Java 类名。
未声明时默认为 `java.lang.Object`。Collection 变量必须声明为 `Iterable` 或数组；声明恰好一个直接类型参数时，该参数必须与 `cf:itemType` 兼容，
其他声明的元素则在运行时逐一校验。并行执行会进一步把输入约束为 List-compatible 声明。可选的输出聚合由原子属性对
`cf:target` 和 `cf:source` 声明：前者引用已声明且 List-compatible 的 Process 变量，后者引用已声明的 `inner`
Process 变量。两个名称必须不同；output target List 声明了类型参数时，该参数必须与 output source 变量类型完全一致。聚合结果按输入索引排序；
顺序聚合仅在循环退出时原子发布。每次迭代开始前，output source 变量都会重置为其 Process 变量声明的默认值。同时省略两个属性时仍会执行全部
迭代，但不收集结果。

## 10. 规范化回写

BPMN writer 保留可执行子集，并使用 `cf:` namespace 写出 CompileFlow 数据。默认值
规范之外的 source 属性会被拒绝。回写不是任意 BPMN 文档的通用 round
trip：模型可被序列化前，不支持的 BPMN 元素和厂商扩展已被拒绝。

修改本契约时，必须在同一变更中同步扩展 XSD、parser、model validator、必要的 writer、generator 或 executor、本双语规范，以及严格解析和
round-trip 测试。
