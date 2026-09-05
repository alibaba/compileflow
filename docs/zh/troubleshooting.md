# 故障排查

排查应从稳定 error code、请求流程身份以及对应 Engine/deploy diagnostics 开始。不要一开始就开启大范围 debug
日志，更不要记录流程变量或源码。

## 1. 启动失败

### 未知或非法配置

CompileFlow Spring property 使用严格绑定。拼错的 `compileflow.engine.*`、`compileflow.deploy.*` 或
`compileflow.workbench.server.*` 字段必须导致启动失败。

1. 读取 binding failure 与属性路径。
2. 与[配置文档](configuration.md)对照。
3. 删除过期 alias，不要在另一个 prefix 下复制同一个设置。
4. Datasource、HTTP server、Actuator 与 logging 使用 Spring 标准前缀。

`CF_CONFIG_001` 表示配置值非法。`CF_CONFIG_005` 通常表示没有匹配格式 provider，或同一
`ProcessModelType` 出现多个 provider；确认 classpath 中只有预期的 `compileflow-tbbpm` 或
`compileflow-bpmn` provider。

### 缺少运行时编译器

CompileFlow 运行时需要标准 `jdk.compiler` 模块。使用完整 JDK，或在自定义 runtime image 中包含该模块。只有 JRE
或过度裁剪的镜像无法编译生成流程。

## 2. 无法加载定义

| Code              | 含义                     | 检查项                                            |
|-------------------|--------------------------|---------------------------------------------------|
| `CF_RESOURCE_001` | 找不到 definition        | 检查 process code 与 classpath resource name      |
| `CF_RESOURCE_002` | 读取或 UTF-8 解码失败    | 检查可读性与 strict UTF-8                         |
| `CF_RESOURCE_003` | resource policy 拒绝输入 | 检查大小与本地 classpath URL                    |

显式打包定义：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath("order.process", "flows/order.bpm");
```

使用与 Engine 模型类型匹配的显式资源路径，例如 TBBPM 使用
`ProcessDefinition.classpath("order.process", "flows/order.process.bpm")`，BPMN 使用
`ProcessDefinition.classpath("order.process", "flows/order.process.bpmn")`。

Network-backed classpath URL 会被拒绝。远程 artifact 必须在 Engine 外获取并校验。

## 3. 编译失败或运行时未就绪

`CF_COMPILE_001` 与 `CF_COMPILE_002` 分别表示流程编译失败与生成 Java 编译失败。`CF_RUNTIME_001` 表示流程运行时在加载或预热期间未就绪（超时、中断或取消）。

1. 对精确定义运行 strict preflight：

```java
ProcessPreflightReport report = engine.tooling()
        .preflight(definition, ProcessPreflightOptions.strict());
```

2. 检查结构化 compiler diagnostics 与失败 process code。
3. 确认 action class/method 对配置 class loader 可见。
4. 只有测量证明合法冷 runtime load 确实超过调用方等待预算时，才增加
   `compileflow.engine.runtime-load-timeout`。

引擎不会自动重试失败的 runtime load。一次 single-flight 操作使用同一份不可变源码快照与 classloader scope，在同一请求内重复
parser、generator 或 javac 失败只会占用有界 runtime-load executor。修复流程定义或运行环境后，下一次请求可以重新发起加载。

runtime-load timeout 不会取消共享 single-flight。已接纳的加载可能在调用返回后成功并写入节点本地缓存；重试前先查看
runtime-load diagnostics 和日志，避免把一次等待超时误判为确定失败。

本地诊断可配置 `compileflow.engine.java-diagnostics.debug.output-directory`。只有确实需要 class output 时才加
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

机器处理使用 `getError().getCode()`。成功 data 可以为 `null`，不能根据 `getOutput()` 推断失败。需要异常边界时，
`orElseThrow()` 将失败值转换为 `ProcessExecutionException`。

常见 code：

| Code          | 含义                                         |
|---------------|----------------------------------------------|
| `CF_EXEC_001` | 流程 action/执行失败                         |
| `CF_EXEC_003` | script failure                               |
| `CF_EXEC_004` | action 或流程 timeout                        |
| `CF_EXEC_005` | 本地有界执行容量耗尽                         |
| `CF_EXEC_007` | 操作被中断                                   |
| `CF_EXEC_008` | 执行校验失败                                 |
| `CF_EXEC_009` | 流程完成后 typed output mapping 失败         |
| `CF_EXEC_010` | 流程开始前 typed input mapping 失败          |
| `CF_EXEC_011` | Alias route 不可用，或在有界选择期间无法稳定 |
| `CF_EXEC_012` | 选定 Runtime 在 action 执行前不可用          |
| `CF_EXEC_013` | 嵌套流程调用超过配置深度                     |
| `CF_EXEC_014` | 解析后的流程调用图在 action 执行前无效       |

输入映射失败表示流程尚未开始。输出映射失败不会回滚流程已经产生的副作用。

## 5. 精确 Version 或 Alias 未 Ready

已发布执行 fail-closed：

- exact version 必须已安装在节点；
- Alias 只读取 local-ready state；
- router 只能选择当前 stable 或 candidate；
- 不存在 previous-version fallback。

依次检查：

1. 控制面 Alias revision 与 stable/candidate version；
2. outbox 状态与 dead letter；
3. `DeployRuntime.snapshot()` 的 desired、in-flight、deployed 与 backed-off version；
4. artifact identity、model type 与 digest；
5. local-ready revision 与有界安装失败原因。

控制面的 `COMPLETED` 只表示 route 事务提交，不代表所有节点已收敛。

`CF_EXEC_011` 表示 Alias route 缺失或发生并发 revision 变化；`CF_EXEC_012` 表示选定 version 尚未在本节点安装或
ready。两者都发生在 process action 之前，但重试必须有界，因为错误 Alias 或不可用 artifact 不会仅靠等待自行恢复。

`CF_EXEC_013` 要求调整刻意配置过小的 `compileflow.engine.call.max-depth`，它不是瞬时重试条件。`CF_EXEC_014`
表示 Process call graph 无效，例如存在环、exact-Version 或 Published graph 声明了 `classpath` target，或目标 Process code
与调用声明不一致。Direct graph 可以声明 `classpath` 或 exact `version` target。应修正定义或发布/安装正确的 exact graph；
执行阶段不会猜测 Alias、latest version 或调用方相对 classpath。

## 6. 重复编译或内存过高

不要为每个请求创建 Engine。每种 model type/configuration 复用一个 Engine，并随应用生命周期关闭。

若发生重复编译：

1. 确认 definition bytes 稳定；
2. 确认请求使用相同有效 class-loader scope；
3. 检查 runtime ownership 是否被立即释放；
4. 检查 `compileflow.engine.max-resident-runtimes`；
5. 启动时 preflight 并 `engine.runtime().warmUp(definition)` 预热已知定义。

不要为重复调用生成随机 version。Published version identity 不可变；内容变化必须使用一个新的明确版本。

## 7. Executor 拒绝

`CF_EXEC_005` 与 `RejectedExecutionException` 表示有界过载，不意味着应把所有 queue 改为无界。

- Runtime-load：
  `compileflow.engine.executor.runtime-load.max-concurrency` 与 `max-pending`。
- Timeout-enforced action：
  `compileflow.engine.executor.action-timeout.max-concurrency` 与 `max-pending`。
- Event：`compileflow.engine.observability.events.max-concurrency` 与 `max-pending`。
- Deploy installation：分布式 runtime 进程使用 `compileflow.deploy.runtime.concurrency` 与
  `queue-capacity`；嵌入式安装准入由引擎 runtime-load 容量推导。

增加上限前应测量 queue wait、service time、rejection rate 与上游并发。offered load 超过可持续吞吐时，应在调用方施加
backpressure。

## 8. Server 持久化异步调用

对于 failed 或 dead-lettered async request：

1. 查询 async invocation record 与 attempt count；
2. 使用 invocation ID 检索 execution log；
3. 确认调用方只提供一个Alias route selector：version 或 Alias；
4. 确认 retry 固定到此前选择的 exact version；
5. 通过 deploy-runtime diagnostics 检查根制品解析、完整性与安装错误；
6. 修复根因后再 requeue dead letter。

Workbench 在持久化异步请求前完成 Alias admission，只持久化选中的 exact version 与有界路由归因，不持久化 routing key 或
attributes。未显式提供 key 时，以持久化 invocation ID 作为 cohort key。调用响应与执行日志公开受控的 effective version、
Alias、route revision 和 target，便于关联每次重试；routing key 与 lease token 始终留在内部。

每次尝试都会在进入流程代码前物化持久化的精确根版本。即使 Alias 已切换且旧 runtime 已从当前节点回收，这也是预期路径。持续出现
`Async invocation runtime could not be loaded` 表示制品可用性、完整性、编译容量或节点本地安装存在问题，系统不会因此静默改为执行
Alias 当前版本。

## 9. 安全诊断数据

`ProcessExecution` 提供 trace 与 invocation ID、process namespace/code、可选的精确已发布 Version 和 timing。终态
`ProcessEvent.ExecutionAttribution` 还可以提供父 invocation 与调用深度、model type、source digest 和准入 Alias
revision/target。有界 error code 也可以安全用于诊断。

禁止记录 routing key、source content、完整本地路径、credential、raw variable、任意应用对象，以及共享生产日志中的未脱敏
compiler output。

## 获取帮助

请提供：

- CompileFlow commit/version 与 Java runtime；
- model type；
- 稳定 error code 与脱敏 message；
- 不含机密逻辑时的最小 definition；
- 与失败相关的精确配置 key；
- 确定性复现步骤。

另见[测试](testing.md)、[监控](monitoring.md)与[运维手册](operations-playbook.md)。
