# 故障排查

故障排查应先查看稳定的错误码、请求对应的流程身份以及引擎或部署诊断信息。不要一开始就开启大范围 DEBUG 日志，更不能记录流程变量或源码。

## 1. 启动失败

### 未知或非法配置

CompileFlow Spring 属性使用严格绑定。可枚举配置源中 `compileflow.engine.*`、`compileflow.deploy.*`、
`compileflow.durable.*` 或 `compileflow.workbench.server.*` 下的未知字段会导致启动失败。
该规则不会检查所有操作系统环境变量的拼写：Workbench 显式 `CONFIG_*` 别名使用白名单，其他环境变量名遵循 Spring 宽松绑定。具体属性源边界见[配置参考](configuration.md)。

1. 查看绑定失败信息和属性路径。
2. 与[配置文档](configuration.md)对照。
3. 删除无效别名，不要在其他前缀下重复同一项配置。
4. 数据源、HTTP 服务、Actuator 和日志配置使用 Spring 标准前缀。

`CF_CONFIG_001` 表示配置值非法。`CF_CONFIG_005` 通常表示没有匹配的语义编译器，或同一 `ProcessModelType` 注册了多个实现。确认类路径中只包含需要的 `compileflow-tbbpm` 或 `compileflow-bpmn` 格式模块。

### 缺少运行时编译器

CompileFlow 运行时需要标准 `jdk.compiler` 模块。请使用完整 JDK，或在自定义运行时镜像中包含该模块。只有 JRE 或过度裁剪的镜像无法编译生成流程。

## 2. 无法加载定义

| Code              | 含义                  | 检查项                       |
| ----------------- | --------------------- | ---------------------------- |
| `CF_RESOURCE_001` | 找不到流程定义        | 检查流程编码与类路径资源名称 |
| `CF_RESOURCE_002` | 读取或 UTF-8 解码失败 | 检查可读性与严格 UTF-8 编码  |
| `CF_RESOURCE_003` | 资源策略拒绝输入      | 检查大小与本地类路径 URL     |

显式打包定义：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.bpm");
```

使用与流程定义模型类型匹配的资源路径，例如 TBBPM 使用
`ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.process.bpm")`，BPMN 使用
`ProcessDefinition.classpath(ProcessModelType.BPMN, "order.process", "flows/order.process.bpmn")`。

指向网络地址的类路径 URL 会被拒绝。远程制品必须在引擎外部获取并完成校验。

## 3. 编译失败或运行时未就绪

`CF_COMPILE_001` 与 `CF_COMPILE_002` 分别表示流程编译失败与生成 Java 编译失败。`CF_RUNTIME_001` 表示流程运行时在加载或预热期间未就绪（超时、中断或取消）。

1. 对精确流程定义执行严格预检：

```java
ProcessPreflightReport report = engine.tooling()
        .preflight(definition, ProcessPreflightOptions.strict());
```

2. 检查结构化编译诊断和失败的流程编码。
3. 确认动作类和方法对配置的类加载器可见。
4. 只有测量证明冷运行时加载确实超过调用方等待预算时，才增加
   `compileflow.engine.runtime-load-timeout`。

引擎不会自动重试失败的运行时加载。同一批合并加载请求使用相同的不可变源码快照和类加载器作用域；在一个请求中重复解析、生成代码或调用 javac，只会继续占用有界的运行时加载执行器。修复流程定义或运行环境后，下一次请求可以重新发起加载。

运行时加载超时不会取消共享的合并加载任务。已经接收的任务可能在调用返回后完成，并写入节点本地缓存。重试前先查看运行时加载诊断和日志，避免将等待超时误判为加载失败。

本地诊断可配置 `compileflow.engine.java-diagnostics.debug.output-directory`。只有确实需要类文件时才设置
`debug.bytecode-enabled=true`。导出代码属于敏感数据，排查后应删除。

## 4. 执行返回失败

预期执行失败是值：

```java
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, variables);

if (result.isFailure()) {
    ProcessError error = result.getError();
    log.warn("Flow failed: code={}", error.getCode());
}
```

程序处理错误时使用 `getError().getCode()`。成功结果可以为 `null`，不能根据 `getOutput()` 是否为空判断失败。需要异常边界时，
`orElseThrow()` 将失败值转换为 `ProcessExecutionException`。

常见错误码：

| Code          | 含义                                     |
| ------------- | ---------------------------------------- |
| `CF_EXEC_001` | 流程动作执行失败                         |
| `CF_EXEC_003` | 脚本执行失败                             |
| `CF_EXEC_004` | 动作或流程超时                           |
| `CF_EXEC_005` | 本地有界执行容量耗尽                     |
| `CF_EXEC_007` | 操作被中断                               |
| `CF_EXEC_008` | 执行校验失败                             |
| `CF_EXEC_009` | 流程完成后类型化输出映射失败             |
| `CF_EXEC_010` | 流程开始前类型化输入映射失败             |
| `CF_EXEC_011` | 别名路由不可用，或在有界选择期间无法稳定 |
| `CF_EXEC_012` | 选定运行时在动作执行前不可用             |
| `CF_EXEC_013` | 嵌套流程调用超过配置深度                 |
| `CF_EXEC_014` | 解析后的流程调用图在动作执行前无效       |

输入映射失败表示流程尚未开始。输出映射失败不会回滚流程已经产生的副作用。

## 5. 精确版本或别名未就绪

已发布流程在状态不完整时会拒绝执行：

- 精确版本必须已安装在当前节点；
- 别名只读取本地就绪状态；
- 路由器只能选择当前稳定版本或候选版本；
- 不会自动回退到其他版本。

依次检查：

1. 控制面的别名修订号、稳定版本和候选版本；
2. Outbox 状态与死信；
3. `DeploymentRuntime.snapshot()` 中的期望、安装中、已部署和退避版本；
4. 制品身份、模型类型和摘要；
5. 本地就绪修订号与安装失败原因。

控制面的 `COMPLETED` 只表示路由事务已经提交，不代表所有节点都已收敛。

`CF_EXEC_011` 表示别名路由缺失或修订号发生并发变化；`CF_EXEC_012` 表示选定版本尚未在当前节点安装或就绪。
两者都发生在流程动作执行之前。重试必须有明确上限，因为错误的别名或不可用制品无法仅靠等待恢复。

`CF_EXEC_013` 表示 `compileflow.engine.call.max-depth` 小于当前调用图所需深度，应增大配置而不是重试。`CF_EXEC_014`
表示流程调用图无效，例如存在循环调用、精确版本或已发布调用图声明了 `classpath` 目标，或目标流程编码与调用声明不一致。
直接调用图可以声明 `classpath` 或精确 `version` 目标。应修正定义，或发布并安装正确的调用图；执行阶段不会猜测别名、
最新版本或相对于调用方的类路径。

## 6. 重复编译或内存过高

不要为每个请求创建引擎。每组资源与配置应复用一个引擎，并随应用生命周期关闭。

出现重复编译时：

1. 确认流程定义内容保持不变；
2. 确认请求使用相同的有效类加载器作用域；
3. 检查运行时所有权是否被过早释放；
4. 检查 `compileflow.engine.max-resident-runtimes`；
5. 启动时预检已知定义，并通过 `engine.runtime().warmUp(definition)` 完成预热。

不要为重复调用生成随机版本。已发布版本的身份不可变；内容变化时必须使用新的明确版本。

## 7. 执行器拒绝任务

`CF_EXEC_005` 与 `RejectedExecutionException` 表示有界容量已经耗尽，不应因此把队列改为无界。

- 运行时加载：
  `compileflow.engine.executor.runtime-load.max-concurrency` 与 `max-pending`。
- 带超时控制的动作：
  `compileflow.engine.executor.action-timeout.max-concurrency` 与 `max-pending`。
- 事件：`compileflow.engine.observability.events.max-concurrency` 与 `max-pending`。
- Deploy 安装：`compileflow.deploy.runtime.installation-concurrency` 同时控制分布式和嵌入式拓扑的安装准入；实际编译和运行时加载还受引擎运行时加载容量限制。

增加上限前应测量队列等待时间、处理时间、拒绝率和上游并发量。当输入负载超过可持续吞吐量时，应由调用方实施背压。

## 8. 服务端持久化异步调用

对于失败或进入死信队列的异步请求：

1. 查询异步调用记录和尝试次数；
2. 使用调用 ID 检索执行日志；
3. 确认调用方只提供一种路由选择方式：版本或别名；
4. 确认重试仍使用此前选定的精确版本；
5. 通过部署运行时诊断信息检查根制品解析、完整性和安装错误；
6. 修复根因后再将死信重新入队。

Workbench 在持久化异步请求前完成别名准入，只保存选中的精确版本和必要的路由信息，不保存路由键或属性。
未显式提供路由键时，使用持久化调用 ID 作为分组键。调用响应和执行日志会记录实际版本、别名、路由修订号和目标，
以便关联每次重试；路由键和租约令牌始终仅供内部使用。

每次尝试都会在进入流程代码前加载持久化的精确根版本。即使别名已经切换，旧运行时也已从当前节点回收，这一规则仍然不变。
持续出现 `Async invocation runtime could not be loaded` 表示制品可用性、完整性、编译容量或节点本地安装存在问题；
系统不会静默改为执行别名当前指向的版本。

## 9. 安全诊断数据

`ProcessExecution` 提供追踪 ID、调用 ID、流程命名空间和编码、可选的精确已发布版本及耗时信息。终态
`ProcessEvent.ExecutionAttribution` 还可以提供父调用、调用深度、模型类型、源码摘要以及准入别名的修订号和目标。
稳定且有界的错误码也可以安全用于诊断。

禁止记录路由键、源码内容、完整本地路径、凭据、原始变量、任意应用对象，以及生产共享日志中未经脱敏的编译器输出。

## 获取帮助

请提供：

- CompileFlow 提交或版本，以及 Java 运行时版本；
- 模型类型；
- 稳定错误码和脱敏后的错误信息；
- 不含机密逻辑的最小流程定义；
- 与失败相关的准确配置项；
- 确定性复现步骤。

另见[测试](testing.md)、[监控](monitoring.md)与[运维手册](operations-playbook.md)。
