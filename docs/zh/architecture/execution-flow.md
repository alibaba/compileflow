# CompileFlow 执行流程

一次流程调用从公共 API 到类型化结果，依次完成请求归一化、流程定义加载、运行时识别、路由、编译和执行信息记录。

第 1–14 节介绍普通 `ProcessEngine`。Durable 的启动、Wait 完成、Timer 和 Effect 恢复以及 Worker 推进使用独立的持久化生命周期，
详见第 15 节。

## 1. 公共入口

标准 Map 入口接收已有 `ProcessRef` 或显式 `ProcessDefinition`、流程变量与
`ProcessExecutionOptions`。

```java
ProcessResult<Map<String, Object>> result =
    engine.execute(ref, variables, options);
```

类型化输入先由 `ProcessDataMapper` 转换，再进入这条执行路径；Map 执行成功后再转换类型化输出。输入映射失败会在流程执行前返回
`CF_EXEC_010`；输出映射失败会在流程完成后返回 `CF_EXEC_009`，已经发生的 Action 副作用不会因此回滚。

`ProcessExecutionOptions` 保存本次调用和路由所需的元数据。路由键与路由属性不会混入流程变量。

## 2. 请求归一化

`DefaultProcessEngine` 将公共输入转换为一个内部 `ProcessRuntimeRequest`：

| 公共输入             | 内部请求                               |
| -------------------- | -------------------------------------- |
| `ProcessDefinition`  | 默认命名空间、流程编码和显式定义来源   |
| `ProcessRef.Version` | 命名空间、流程编码和精确版本           |
| `ProcessRef.Alias`   | 命名空间、流程编码和等待路由选择的别名 |
| 精确版本的本地加载   | 精确版本引用和显式流程定义             |

流程定义编码必须与版本引用中的流程编码一致。直接定义需要显式指定模型类型，并使用文档规定的默认命名空间；
系统不会为它补充隐式版本或别名。

## 3. 执行作用域

解析运行时之前，引擎会创建操作作用域和 `EngineExecutionContext`，其中包含：

- 调用 ID；
- 路由键和不可变路由属性；
- 请求流程引用；
- 解析过程中确定的实际路由信息；
- 开始与完成时间。

`EngineExecutionContextHolder` 使用 `ThreadLocal` 在限定作用域内向内部组件传递上下文。引擎始终在 `finally` 中清理，
不会把它当作全局业务状态。

生命周期事件由一组封闭的类型化 `ProcessEvent` 记录组成。完成和失败事件包含返回给调用方的 `ProcessExecution`，运维信息则放在独立的
`ProcessEvent.ExecutionAttribution` 中；失败事件还包含类型化的 `ProcessError`。开始事件记录命名空间、流程编码和调用 ID，
Trigger 开始事件还记录触发信息。链路追踪 ID 与事件时间是公共字段。事件不能携带路由键、流程变量、流程定义内容、任意元数据或原始异常。

公共 `ProcessExecution` 是封闭类型，只包含链路追踪 ID、调用 ID、命名空间、流程编码、可选的精确发布版本以及开始和完成时间。
运维事件信息还包含父调用、调用深度、模型类型、流程定义摘要和别名准入结果。两者都不提供任意元数据 Map。

## 4. 版本解析

`ProcessRuntimeResolver` 解析有效版本：

1. `ProcessRef.Version` 已指向精确版本，无需选择别名。
2. `ProcessRef.Alias` 从配置的 `ProcessAliasRouteSource` 读取路由，先应用路由绑定的具名
   `ProcessAliasTargetingPolicy`，未覆盖时再使用协议固定的百分比分桶。
3. 所有 `ProcessDefinition` 变体都不带版本。

已发布别名的选择遵循有限重试的路由接管规则：

- 所选目标必须是当前别名修订中的稳定版本或候选版本，路由指定的具名策略也必须在本节点可用；
- 路由交接期间若无法获得精确运行时，核心引擎会重新读取路由并再执行一次完整准入；
- 引擎在执行前保留选中的根流程运行时，与流程格式无关；
- 路由接管成功后，之后到达的别名修订只影响后续调用。

选择当前别名路由之外的目标，或缺少路由指定的策略，都会使请求直接失败。重新选择只会完整评估更新后的服务路由，
不会回退到上一版本、猜测最新版本或在单次请求中随机降级。

在部署节点上，精确版本必须存在于 `InstalledVersionState`。本地尚未准备就绪时，执行路径不会根据版本标识临时发起远程读取。

## 5. 绑定键与精确运行时标识

缓存使用两个相关的键：

- 绑定键：`namespace#code` 或 `namespace#code#version`，诊断中常简写为 `code#version`；
- 精确的 `ProcessRuntimeIdentity`。

绑定键用于查找当前与某个流程坐标关联的精确运行时，不能单独标识编译结果。

`ProcessRuntimeIdentity` 由模型类型、流程编码、流程定义摘要、引擎本地编译指纹和类加载器标识共同确定。
编译指纹与类加载器按对象身份比较；诊断字符串只包含摘要前缀和身份说明。

## 6. 加载精确流程定义

仅包含流程引用的请求命中绑定缓存时，可以直接返回运行时；否则请求必须携带来源明确的 `ProcessDefinition`。

`DefaultProcessDefinitionLoader` 执行一次有界读取：

- 内联内容使用 UTF-8 编码，并按字节数校验大小；
- 类路径查找拒绝由网络地址提供的资源；
- 每个 stream 最多读取 `maxBytes + 1`；
- 非法 UTF-8 内容会在解析前失败。

读取结果是不可变的 `ProcessDefinitionSnapshot`，包含原始字节、解码内容、安全的来源说明和 SHA-256 摘要。
解析、运行时识别和编译共用同一份快照；文件路径或资源位置不能代替内容身份。

## 7. 缓存与并发加载合并

`DefaultProcessRuntimeLoader` 先按精确的 `ProcessRuntimeIdentity` 查询缓存。未命中时进入
`InflightRuntimeLoadRegistry`：

```text
putIfAbsent(ProcessRuntimeIdentity, proposed future)
  -> 已有 future：等待同一结果
  -> proposed future：提交一次编译
```

所有等待者会收到相同的成功结果或失败信息。任务完成后移除并发加载记录，使后续请求可以再次尝试此前失败的编译。

`ProcessRuntimeLoader` 不会在公共调用路径上长期持有生命周期锁：

- `AtomicBoolean` 在关闭开始后拒绝新调用；
- `InflightRuntimeLoadRegistry` 通过短临界区协调任务注册和关闭，并取消未完成的 Future；
- 发布编译结果时使用另一段短临界区，确保服务关闭后不再安装结果。

## 8. 编译

运行时加载器先完成语义编译，再创建选定的运行时：

```text
DefaultProcessDefinitionLoader.load
  -> ProcessDefinitionSnapshot
  -> 解析并转换为格式模型
  -> 结构与语义校验
  -> ProcessSemanticCompilation
  -> ProcessRuntimeFactory（COMPILED 或 INTERPRETED）
  -> ProcessRuntime
```

两个运行时工厂使用同一份语义编译结果，都不负责解析流程定义或执行语义编译。
编译模式的工厂生成并编译 Java 流程代码；解释模式的工厂准备执行所需的 Action 和表达式。
加载器在语义编译和运行时创建期间使用请求指定的 ClassLoader，并在成功或失败后恢复 Worker 原来的线程上下文 ClassLoader。
流程预检中的定义加载和静态检查也使用该 ClassLoader，包括只执行静态检查的请求。

单次编译不会自动重试。解析、代码生成、javac、超时和配置错误都会保留各自的类型化错误；同一次并发加载的所有等待者会收到相同失败。
任务完成后会移除并发加载记录，因此修正流程定义或运行环境后，后续请求可以重新编译。

只有显式启用时才会导出调试产物。诊断文件按包路径写入配置目录下的 `source/`、`metadata/` 和可选的 `classes/`。
这些文件不影响运行时身份；导出失败会使准备过程失败，不会被忽略。

## 9. 条件安装

编译完成后，`runtimeCache.install` 按条件更新绑定：

- 不可变版本不能绑定到不同内容；
- 无版本绑定只有在预期包装对象仍持有它时才能更新；
- 不覆盖其他并发操作产生的较新绑定；
- 精确运行时仍可由其他绑定和所有者共享。

缓存容量有上限，并按持有关系管理；只有没有所有权引用的条目可以淘汰。

每次执行都会在业务代码开始前准备并保留完整的静态流程调用图。已发布流程调用使用调用点声明的精确子流程版本；
直接流程调用绑定到精确的类路径资源或版本。路由收敛可以立即释放过期的部署所有权，但本次调用持有的运行时会保留到调用结束。
直接 `execute`、别名准入、嵌套调用和 `trigger` 共用同一引擎边界。操作门在路由选择和执行期间保持准入状态；关闭时停止接收新调用，
并等待已经准入的调用完成。所有资源清理步骤共用剩余的关闭期限，不会分别重新计算宽限时间。

流程预检要求试编译时会调用 `runtimeCheckSync`。它可以复用或创建精确运行时，但不会发布版本或修改路由。

## 10. 运行时执行

`DefaultProcessEngine` 调用已经取得的 `ProcessRuntime`。顺序节点按流程图顺序执行；并行分支使用引擎的执行策略，
汇合后才继续执行后续节点。超时取消采用协作式语义，Action 代码应在适用时响应线程中断。

引擎将已知执行失败转换为 `ProcessError` 并返回 `ProcessResult.failure(...)`。JVM 的严重 `Error` 不会转换成普通结果。
执行成功时返回输出变量 Map 和对应的执行信息。

嵌套流程调用沿用外层执行作用域，并返回自身结果。生成代码只标识调用点，执行上下文会在已解析的调用图中精确查找一次目标流程；
不会根据子流程编码推导类路径，也不会通过别名或所谓的最新版本路由子流程。嵌套的 `ProcessExecutionException`
会在保留类型化错误的前提下转换。

## 11. Trigger 流程

`trigger(...)` 使用相同的请求归一化、路由、运行时解析、执行上下文、错误和结果处理流程。

取得运行时后，引擎要求流程实现 `TriggerableProcess`，再根据入口节点 ID 和可选事件执行。每次调用都依据传入上下文
创建新的内存实例，不提供持久化流程实例、继续执行、外部消息关联或故障恢复。

## 12. 本地管理操作

`ProcessRuntimeManager` 提供以下引擎本地管理能力：

- `warmUp(ProcessDefinition...)` 编译精确流程定义，但不创建公共绑定；
- `load(ProcessRef.Version, ProcessDefinition)` 创建不可变的本地版本绑定；
- `unload(ProcessRef.Version...)` 释放精确版本绑定的本地所有权。

`ProcessToolingService.preflight(...)` 在不运行流程的情况下完成校验，并可选择试编译。预热使用引擎统一限制的运行时加载容量，
没有独立的并发开关。本地加载和预热都不同于 `ProcessDeploymentService.publish`：它们既不写入持久化发布状态，也不修改别名。

## 13. 敏感数据边界

允许记录：

- 调用 ID；
- 链路追踪 ID；
- 命名空间与流程编码；
- 请求的流程引用；
- 实际版本或别名；
- 别名修订号及稳定版本或候选版本目标；
- 模型类型与流程定义摘要；
- 有界错误码与安全错误信息；
- 耗时信息。

禁止记录：

- 路由键；
- 流程定义内容；
- 完整本地路径；
- 凭据；
- 原始流程变量；
- 任意应用对象；
- 公共结果中的原始异常上下文。

## 14. 源码导航

- [`DefaultProcessEngine`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/DefaultProcessEngine.java)
- [`ProcessRuntimeRequest`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/ProcessRuntimeRequest.java)
- [`ProcessRuntimeResolver`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/resolution/ProcessRuntimeResolver.java)
- [`DefaultProcessDefinitionLoader`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/source/loader/DefaultProcessDefinitionLoader.java)
- [`ProcessDefinitionSnapshot`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/source/ProcessDefinitionSnapshot.java)
- [`ProcessRuntimeIdentity`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/ProcessRuntimeIdentity.java)
- [`DefaultProcessRuntimeLoader`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/loading/DefaultProcessRuntimeLoader.java)
- [`InflightRuntimeLoadRegistry`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/loading/InflightRuntimeLoadRegistry.java)
- [`EngineExecutionContext`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/context/EngineExecutionContext.java)

## 15. Durable 执行流程

Durable 使用独立 API 和事务状态机：

```text
start(显式 Definition | 精确 Version | Alias)
  -> 加载精确流程定义，或解析一次 Version/Alias
  -> 保存不可变流程语义和精确的静态调用绑定
  -> 当前部署准备可重新生成的代码和应用能力
  -> 提交绑定 processId、状态为 RUNNABLE 的 Run
  -> Turn Worker 领取带修订号和随机令牌的租约
  -> 执行到 Wait / Timer / Effect Action / 终止边界
  -> Store 原子提交 Run、边界、Journal 和选定的 Integration Event
  -> Wait 完成、Timer 或 Effect 结果生成唯一的恢复消息
  -> 下一 Turn 从可移植的已提交检查点恢复
```

Durable 不会调用普通 `trigger(...)` 恢复流程，也不会把 Java 调用栈或任意对象写入数据库。别名只在启动时解析一次并确定精确版本；
恢复只使用已保存的 Process ID，不再解析别名、版本或流程定义位置。完整时序、状态机、故障恢复和数据所有权见
[Durable 架构](durable-architecture.md)。

## 相关文档

- [版本路由](version-routing.md)
- [Durable 架构](durable-architecture.md)
- [高级特性](../advanced-features.md)
- [资源管理](../resource-management.md)
