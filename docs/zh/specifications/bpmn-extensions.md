# CompileFlow BPMN 扩展规范

本规范定义 CompileFlow 在受支持 BPMN 2.0 可执行子集中拥有的 XML 词汇，涵盖扩展语法和执行约束。标准 BPMN
元素的支持范围单独记录在[节点支持列表](../node-support.md)中。

## 1. 命名空间与校验

CompileFlow 扩展使用以下命名空间：

```xml
xmlns:cf="http://www.compileflow.org"
```

严格解析会校验两份随模块提供的 Schema：

- [`BPMN20.xsd`](../../../compileflow-bpmn/src/main/resources/BPMN20.xsd) 校验标准 BPMN 结构；
- [`CompileFlowBpmnExtensions.xsd`](../../../compileflow-bpmn/src/main/resources/CompileFlowBpmnExtensions.xsd)
  校验 CompileFlow 扩展元素、属性和封闭值域。

Schema 无法表达扩展元素的归属关系和全部代码生成约束。因此完成 Schema 校验后，BPMN 解析器与模型校验器
还会继续执行本规范中的归属和语义校验。未知命名空间、未知 `cf:` 元素、不受支持的属性、重复的单例扩展，
以及叶子扩展元素中的子元素都会被拒绝，不会被忽略。

引擎从自身类路径加载 Schema，并禁止访问外部 DTD 和 Schema。BPMN 文件运行时不需要
`xsi:schemaLocation` 提示。

显式声明的表达式 `xsi:type` 必须解析为 BPMN 模型命名空间中的 `tFormalExpression`，前缀名称本身不限。
即使关闭 Schema 校验，未知或未绑定的命名空间仍然非法。定时器字面量上显式声明的表达式元数据同样需要校验；
未声明表达式元数据的字面量不要求 Java 语言声明。单例策略不能通过多个 `extensionElements` 容器重复声明。

## 2. 扩展归属

顶层扩展放在对应 BPMN 元素的 `extensionElements` 中；Action 映射与策略是 `cf:action` 的直接子元素，
用于核对结果的 Action 嵌套在 Effect 策略中。

| 扩展                  | 所属元素                    |       数量 | 用途                           |
| --------------------- | --------------------------- | ---------: | ------------------------------ |
| `cf:var`              | `process`                   | 零个或多个 | 声明流程变量。                 |
| `cf:input`/`output`   | `cf:action`                 | 零个或多个 | 映射 Action 输入和可选返回值。 |
| `cf:input`/`output`   | `scriptTask`                | 零个或多个 | 映射脚本输入和可选返回值。     |
| `cf:input`/`output`   | `callActivity`              | 零个或多个 | 映射被调用流程的输入和输出。   |
| `cf:action`           | `serviceTask`               |   恰好一个 | 声明任务实现。                 |
| `cf:invocationPolicy` | `cf:action` 或 `scriptTask` | 零个或一个 | 管理同步超时、重试和最终失败。 |
| `cf:effectPolicy`     | `cf:action` 或 `scriptTask` | 零个或一个 | 管理 Effect 恢复。             |

限定属性 `cf:collection`、`cf:item`、`cf:itemType`、`cf:index`、
`cf:target` 和 `cf:source` 直接属于 `multiInstanceLoopCharacteristics`，不是子扩展元素。
`cf:execution` 直接属于 `scriptTask`。

扩展出现在其他元素上即为非法。`serviceTask` 不能是空任务，流程预检要求它恰好包含一个
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
| `id`           |   否 | XML 标识元数据。               |
| `description`  |   否 | 说明文字。                     |

流程变量名必须唯一，同时作为生成字段名和上下文 Map 的键。根变量属于声明，而不是映射；带方向的 `source` 与 `target`
属性只用于 Action 和流程调用。

变量方向同时规定数据所有权：`param` 是调用方提供的输入，`return` 是流程输出，`inner` 是流程内部状态。
`ProcessEngine.execute` 与 Durable 启动只接受由 `param` 组成的封闭 Map，可以省略部分参数；传入 `return`、`inner`
或未声明字段会直接失败。普通 Trigger 入口使用调用方提供的状态启动一次新调用，可以接收任意已声明的根变量，
但不会恢复 Durable 的后续执行位置。

Action 输入读取 `source` 并写入 Action 的局部 `target`，且 `source` 与 `defaultValue` 必须且只能声明一个。Action
输出的来源是 Action 返回值，因此只需显式声明 `target`。流程调用两端都显式声明：输入把调用方 `source` 映射到
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

每个 `serviceTask` 必须恰好包含一个 `cf:action`。服务任务调用应用提供的实现，其 `type` 只能是
`java` 或 `spring-bean`。

| `type`        | 实现契约                                                    |
| ------------- | ----------------------------------------------------------- |
| `java`        | `class`、可选 `method` 和可选 `cf:input`/`cf:output` 映射。 |
| `spring-bean` | `bean`、`class`、可选 `method` 和可选输入/输出映射。        |

`cf:code` 只用于 Effect 恢复策略中脚本类型的 `cf:reconcileAction`，不属于 `serviceTask` 的实现结构。

`execution` 可取 `replayable` 或 `effect`，表示与 TBBPM 相同的 Action 级 Durable 语义。普通 `ProcessRuntime`
同步执行时不使用该属性；Durable 要求显式声明，并将其转换为可重放步骤或 Effect 边界。Action 只能只读访问流程状态中的对象；
状态变化必须通过显式输出完成，组件和 Provider 自行保证生命周期管理及并发调用安全。

## 5. 原生脚本任务

BPMN `scriptTask` 使用标准 BPMN 字段描述实现，CompileFlow 扩展只负责变量映射：

```xml
<scriptTask id="calculate" scriptFormat="java">
  <extensionElements>
    <cf:input target="price" dataType="java.math.BigDecimal"
            source="price"/>
    <cf:output dataType="java.math.BigDecimal"
            target="total"/>
  </extensionElements>
  <script>return price.multiply(new java.math.BigDecimal("1.20"));</script>
</scriptTask>
```

`scriptFormat` 和 `script` 都必须非空。脚本源码属于流程定义，`scriptFormat` 用于选择已注册的
`ScriptExecutor`。Java 与 QL 共用这一标准 BPMN 结构和同一份语义计划。每个脚本输入与输出都以
`cf:input` 或 `cf:output` 显式声明。

Java 脚本内容是方法体。内置执行器在加载流程运行时时生成类型明确的包装类，并使用 `javac --release 17` 编译。
`ScriptProgram` 只属于对应的临时运行时，不会作为流程身份持久化。脚本语言、原始内容和声明签名始终绑定到不可变流程版本，
因此可以随时从流程定义重新准备运行时。脚本输入和输出仅支持 JDK 平台类型。Java 脚本在应用进程内运行可信代码，
不提供安全沙箱；不可信的 Workbench 代码必须交给隔离的 Code Runner 执行。

CompileFlow 的执行控制直接声明在脚本任务上：`cf:execution` 是带命名空间的任务属性，
`cf:invocationPolicy` 与 `cf:effectPolicy` 是 `extensionElements` 的直接子元素。`cf:execution` 默认为
`replayable`；有副作用的脚本声明 `cf:execution="effect"`。`serviceTask` 不得包含脚本 Action。

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

| 属性                | 默认值                 | 约束                                                    |
| ------------------- | ---------------------- | ------------------------------------------------------- |
| `timeout`           | 不超时                 | 整个调用的正 ISO-8601 duration。                        |
| `attemptTimeout`    | 不超时                 | 单次尝试的正 ISO-8601 duration；不得大于 `timeout`。    |
| `maxAttempts`       | `1`                    | `1` 到 `100` 的整数，包含首次调用。                     |
| `initialBackoff`    | 重试时为 `PT1S`        | 非负 ISO-8601 duration，精确到整毫秒。                  |
| `backoffMultiplier` | `1.0`                  | 不小于 `1.0` 的有限数值。                               |
| `maxBackoff`        | `100 * initialBackoff` | 非负 ISO-8601 duration，精确到整毫秒。                  |
| `jitter`            | `full`                 | `full` 或 `none`。                                      |
| `retryOn`           | `always`               | `never`、`transient`、`always` 或已注册的重试策略名称。 |
| `onFailure`         | `propagate`            | `propagate`、`continue` 或已注册的失败策略名称。        |

`timeout` 包含全部尝试与重试退避。每次尝试取 `attemptTimeout` 和剩余总预算中的较小值；单次尝试超时可以重试，
总超时不会重试，而是直接交给 `onFailure` 处理；超过总截止时间后也不会开始新的尝试。超时取消采用协作式语义，无法回滚外部副作用。
该策略只管理 `ProcessEngine` 中的一次同步 Action 调用，不用于 Durable 任务或 Effect。只有未声明 `execution` 或声明为
`replayable`，且实现能够承受重试和超时不确定性时，才能使用该策略。`effect` Action 禁止声明 `cf:invocationPolicy`；
普通引擎只同步调用一次，Durable 则将其保存为已经提交的 Effect。

## 7. Durable Effect Policy

带 `execution="effect"` 的 Action 可以在 `cf:action` 内、映射元素之后声明一个 `cf:effectPolicy`。省略该策略时使用
`manual` 模式：只尝试执行一次，结果无法确定时等待人工处理，并且不声明自动恢复参数。
`recovery="retry"` 要求 `maxAttempts`（`2..100`）和不超过 24 小时的正 `recoveryDelay`；
`recovery="reconcile"` 要求 `maxAttempts`（`1..100`）、`maxReconcileAttempts`（`1..1000`）、正 `recoveryDelay`
以及恰好一个 `cf:reconcileAction`。`retry` 和 `reconcile` 模式可以设置 `maxRecoveryDuration`，其值必须为正且不超过 30 天。
`reconcileAction` 用于查询外部操作结果，不能声明输出、默认值、执行模式、调用策略或嵌套 Effect 策略。
每个输入的 `source` 表示原始 Effect 请求中已经持久化的字段，`target` 表示核对操作的调用参数；`source` 不是流程表达式。
除此之外只允许使用稳定的 Kernel 元数据字段 `__cf_effect_id`。通过该字段注入的 Action 输入参数不属于持久化请求字段。
尝试次数属于运行时遥测信息，不能映射为 Action 或核对操作的业务参数。核对操作返回 `EffectReconcileOutcome`，
确认结果仍通过原始 Effect Action 的输出映射写回流程状态。

在结构化集合中，如果每次 Effect 需要使用不同的恢复方案，`cf:effectPolicy` 可以改为声明
`recoveryPlanVariable="variableName"`。该可见变量必须声明为
`com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan`，且不得同时声明静态恢复属性。
状态机在创建 Effect 时校验并固定所选值；若可能选择 `RECONCILE`，仍须提供静态
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

`calledElement` 是被调用流程的规范编码，`cf:classpath` 与 `cf:version` 必须且只能提供一个。`cf:classpath` 是
应用类路径中精确且规范化的资源路径，不是 URI，也不支持协议前缀、父目录跳转、通配符或相对于调用方解析。
直接流程定义可以调用类路径资源或精确版本；按精确版本执行的流程和已发布流程只能调用调用方命名空间中的精确版本。
别名只用于根流程准入。调用图会在业务代码执行前完成解析；Durable 恢复时使用已持久化的精确流程目标和调用点标识，
不会重新读取当前流程定义或路由。Durable 在同一 Run 内使用另一个 `ProcessInvocation` 帧执行被调流程，不会创建可独立寻址的 Run。精确被调流程的 `param`
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
其他声明的元素则在运行时逐一校验。并行执行还要求输入变量声明为兼容 `java.util.List` 的类型。可选的输出聚合通过一组不可拆分的属性
`cf:target` 和 `cf:source` 声明：前者引用已声明且兼容 `java.util.List` 的流程变量，后者引用已声明的 `inner` 流程变量。
两个名称必须不同；目标 List 声明类型参数时，该参数必须与来源变量类型完全一致。聚合结果按输入索引排序；
顺序聚合只在循环退出时一次性写回。每次迭代开始前，来源变量都会重置为流程变量声明的默认值。同时省略两个属性时仍会执行全部
迭代，但不收集结果。

## 10. 规范化回写

BPMN 写出器只保留可执行子集，并使用 `cf:` 命名空间写出 CompileFlow 数据。
规范之外的源属性会被拒绝。该写出能力不保证任意 BPMN 文档都能无损往返转换；不受支持的 BPMN 元素和扩展会在模型序列化前被拒绝。
