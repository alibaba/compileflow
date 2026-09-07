# 流程格式参考

CompileFlow 接受 TBBPM 和文档明确支持的 BPMN 2.0 子集。本页说明两种格式的语法对应关系及其共用运行时边界。

## 1. 格式定位

| 维度     | TBBPM                                                                 | BPMN 2.0                                                             |
| -------- | --------------------------------------------------------------------- | -------------------------------------------------------------------- |
| 全称     | Taobao Business Process Model                                         | Business Process Model and Notation 2.0                              |
| 规范来源 | CompileFlow 维护的[TBBPM 规范](tbbpm.md)                              | OMG 正式规范                                                         |
| 可移植性 | CompileFlow 原生                                                      | 使用标准 BPMN 词汇与模型交换格式；执行能力仍取决于子集和扩展         |
| 适用目标 | 面向 CompileFlow 的自动化流程                                         | 使用已记录 BPMN 子集的流程定义                                       |
| 持久化   | `ProcessEngine` 不保存后续执行位置；Durable 支持文档列出的 TBBPM 能力 | `ProcessEngine` 不保存后续执行位置；Durable 支持文档列出的 BPMN 能力 |

## 2. 执行模型

两种格式都经过一次归一化并生成 `ProcessSemanticPlan`。`ProcessEngine` 随后选择编译执行或解释执行的
`ProcessRuntime`。编译模式生成与格式无关的 Java 源码，使用 JDK Java Compiler（`--release 17 -proc:none`）编译，
再通过隔离的 `ClassLoader` 加载。

两种格式共享运行时实现，差异主要在语法、支持元素、模型交换方式和扩展归属。Durable 使用独立执行入口，
两个格式前端共用 `DurableMachineLowerer`、Java 程序编译器和状态机解释器。

## 3. 元素对应关系

### 3.1 控制流

| 能力     | TBBPM     | BPMN 2.0 受支持子集 | 说明 |
| -------- | --------- | ------------------- | ---- |
| 开始节点 | `<start>` | `<startEvent>`      | 等价 |
| 结束节点 | `<end>`   | `<endEvent>`        | 等价 |

### 3.2 任务节点

| 能力          | TBBPM                             | BPMN 2.0 受支持子集                                                                         | 说明                                               |
| ------------- | --------------------------------- | ------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| 自动任务      | 带 `<action>` 的 `<autoTask>`     | 带命名空间 `<cf:action>` 的 `<serviceTask>`                                                 | 共用内置 Action 实现                               |
| 脚本任务      | 带 Action 的 `<scriptTask>`       | `<scriptTask scriptFormat="..."><script>...</script>`                                       | 共用脚本注册表，XML 结构不同                       |
| 命名触发入口  | `<waitTask>`                      | `<receiveTask>`                                                                             | 普通引擎启动新调用；Durable 恢复精确的 Wait        |
| 事件触发入口  | `<waitEventTask>`                 | 中间消息捕获事件                                                                            | Durable 根据权威 Wait 令牌确定精确 Run、节点和事件 |
| 持久化定时器  | `<timerTask>`                     | 中间定时器捕获事件                                                                          | 普通引擎没有持久化调度器，遇到该节点会失败         |
| Effect Action | 流程任务上的 `execution="effect"` | `serviceTask` 的 `cf:action execution="effect"`，或 `scriptTask` 的 `cf:execution="effect"` | Durable 执行语义，不是另一种业务节点               |
| User task     | 不支持                            | 拒绝                                                                                        | 人工任务生命周期不属于 CompileFlow                 |

### 3.3 网关

| 能力      | TBBPM         | BPMN 2.0 受支持子集  | 说明                                           |
| --------- | ------------- | -------------------- | ---------------------------------------------- |
| Exclusive | `<exclusive>` | `<exclusiveGateway>` | 等价                                           |
| Parallel  | `<parallel>`  | `<parallelGateway>`  | 等价                                           |
| Inclusive | `<inclusive>` | `<inclusiveGateway>` | 等价                                           |
| 事件网关  | 不支持        | 不支持               | 需要同时订阅多个事件并竞争触发，不在支持范围内 |
| 复杂网关  | 不支持        | 不支持               | 不在支持范围内                                 |

### 3.4 子流程与循环

| 能力              | TBBPM                    | BPMN 2.0 受支持子集                  | 说明                                          |
| ----------------- | ------------------------ | ------------------------------------ | --------------------------------------------- |
| 内嵌流程/流程调用 | `<subBpm>` / `<bpmCall>` | `<subProcess>` / `<callActivity>`    | 两种格式都区分嵌套作用域与流程调用            |
| foreach           | `<foreach>`              | `<multiInstanceLoopCharacteristics>` | 支持顺序和确定性有序并行模式                  |
| while             | `<while>`                | `<standardLoopCharacteristics>`      | 等价的有界条件语义                            |
| `break`           | `<break>`                | 不支持                               | TBBPM 特有                                    |
| `continue`        | `<continue>`             | 不支持                               | TBBPM 特有                                    |
| 并行多实例        | `execution="parallel"`   | `isSequential="false"`               | 仅 Durable；List 输入、有界下发、可选有序聚合 |

### 3.5 其他

| 能力        | TBBPM                                                                          | BPMN 2.0 受支持子集                                                   | 说明                                                |
| ----------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------- | --------------------------------------------------- |
| 注释        | `<note>`                                                                       | 拒绝 `textAnnotation`                                                 | TBBPM note 仅用于设计；当前 BPMN 子集不保留文本注释 |
| Data object | 不支持                                                                         | 不支持                                                                | 使用流程变量                                        |
| 消息/信号   | 普通引擎不提供消息代理语义；Durable 的 `waitEventTask` 恢复令牌对应的精确 Wait | `receiveTask` 与中间消息捕获事件转换为精确 Durable Wait；拒绝信号节点 | 不提供通用的消息广播、信号广播或关联匹配            |
| 部署身份    | 位于 XML 之外的 `ProcessRef` 和部署元数据                                      | 位于 XML 之外的 `ProcessRef` 和部署元数据                             | 共用引擎契约                                        |

## 4. Action

TBBPM 将 Action 作为一级元素：

```xml
<autoTask id="task1" g="80,0,120,48">
    <action type="spring-bean" bean="userService"
            class="com.example.UserService" method="getUser"/>
</autoTask>
```

内置调用类型包括 `java`、`spring-bean` 和 `script`；脚本 Action 通过 `language` 选择 QL、Java 或其他已注册语言。完整契约见
[TBBPM 规范](tbbpm.md)。

BPMN 服务任务通过标准扩展元素使用同一 Action 模型：

```xml
<serviceTask id="task1">
    <extensionElements>
        <cf:action type="spring-bean" bean="userService"
                   class="com.example.UserService" method="getUser"/>
    </extensionElements>
</serviceTask>
```

两种格式共享 Java、Spring Bean、内置 QL/Java 脚本语言和显式注册的脚本 Provider。Java 脚本执行器在应用进程内运行可信代码，
不提供安全沙箱。TBBPM 使用直接元素；BPMN 将 CompileFlow 扩展数据放在命名空间中。BPMN `scriptTask` 使用标准
`scriptFormat` 与 `script` 字段，不包装为 `cf:action`。完整 `cf:` 契约见 [BPMN 扩展规范](bpmn-extensions.md)。

## 5. TBBPM 格式

TBBPM 提供：

- CompileFlow 原生流程定义；
- `break` 和 `continue`，这两个元素不属于受支持的 BPMN 子集；
- 不依赖 BPMN 扩展容器的一级 Action 元素；
- 本仓库记录的 TBBPM 示例和编辑器行为。

## 6. BPMN 格式

BPMN 格式提供：

- 受支持元素的标准 BPMN XML 词汇；
- 通过 BPMN XML 和 DI 几何信息交换模型；
- `cf:` 命名空间下的 CompileFlow 执行属性；
- Workbench BPMN 设计器提供的导入和校验。

CompileFlow 只执行上述 BPMN 子集。人工任务、事务、编排协作等不受支持的节点会在解析或流程预检
阶段明确失败。并行多实例仅支持 Durable；可选有序聚合使用 CompileFlow 显式输出扩展。TBBPM 的 `break`、`continue`
和内置业务属性在当前 BPMN 子集中没有直接映射。

## 7. 格式映射边界

### 7.1 TBBPM → BPMN 2.0

TBBPM 到 BPMN 的直接映射包括：`start`/`end`、`autoTask`→`serviceTask`、`exclusive`→`exclusiveGateway`、
`parallel`→`parallelGateway`、`inclusive`→`inclusiveGateway`、`subBpm`→`subProcess`、
`bpmCall`→`callActivity`、`foreach`→`multiInstanceLoopCharacteristics`。

TBBPM `while` 映射到 BPMN `standardLoopCharacteristics`。`break` 与 `continue` 没有受支持的 BPMN
等价项；如果需要 BPMN 子集，应使用受支持的元素表达相同业务语义。部署身份位于两种 XML 之外，因此不影响格式映射。

### 7.2 BPMN 2.0 → TBBPM

上述直接映射可以反向使用。BPMN `multiInstanceLoopCharacteristics` 映射到 TBBPM `foreach`，
`standardLoopCharacteristics` 映射到 TBBPM `while`。User task、transaction、choreography 等不受支持的 BPMN
节点无法用受支持的 TBBPM 节点表示。

## 8. 性能

TBBPM 与 BPMN 共用编译执行引擎。首次执行包含准备成本，后续执行可复用精确运行时。当前 JMH 边界和报告规则见
[性能测试说明](../../../compileflow-benchmarks/README.md)。该模块测量 CompileFlow 运行时路径和手写 Java 基线。

在本地运行性能测试：`./mvnw -pl compileflow-benchmarks verify -Pbenchmarks`。

## 9. 延伸阅读

- [TBBPM 规范](tbbpm.md)
- [BPMN 扩展规范](bpmn-extensions.md)
- [何时使用 CompileFlow](../when-to-use.md)
- [支持面清单](../architecture/supported-surfaces.md)
- [扩展指南](../extension-guide.md)
