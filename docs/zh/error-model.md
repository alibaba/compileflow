# 执行失败模型

CompileFlow 明确区分流程结果、边界异常与 HTTP 传输异常。三者不能混为一谈：调用方必须知道流程是尚未开始、执行中失败，还是已经完成但响应转换失败。

## Engine API

`execute(...)` 和 `trigger(...)` 对执行管线中的失败返回 `ProcessResult<T>`。每个结果都携带受控的
`ProcessExecution` 归因，并且只包含一种结果：

- 成功：`data`，没有 error；
- 失败：`ProcessError`，包含稳定的 `code` 与安全、面向人的 `message`。

`ProcessExecution` 只暴露 trace 与 invocation ID、namespace、process code、可选的精确已发布 Version，以及开始/完成时间。终态
`ProcessEvent` 使用独立的 `ExecutionAttribution` 携带父 invocation 与调用深度、model type、source digest 和准入 Alias 事实。
两者都明确排除 routing key、流程变量、source content、任意 metadata 和原始异常。

调用方应根据 `isSuccess()` 或 `getError().getCode()` 分支，绝不能解析 message 文本。无参
`orElseThrow()` 会抛出 `ProcessExecutionException`，并保留相同的 `ProcessError` 与内存中的
`ProcessExecution`。

非法 Java API 参数会在 invocation 被接受前直接抛出。Typed adapter 失败返回结果，因为调用方需要据此判断重试语义：

- 非法流程定义（包括 XSD 违规）在解析或 preflight 阶段抛出 code 为 `CF_VALIDATION_002` 的
  `CompileFlowException`；这是建模错误，不是基础设施故障；
- input mapping 在流程开始前返回 `CF_EXEC_010`；
- output mapping 在流程完成后返回 `CF_EXEC_009`，并明确提示副作用可能已经发生；
- Alias 路由缺失或并发变化在任何流程 action 运行前返回 `CF_EXEC_011`；
- 选定 Runtime 在本节点不可用时，在任何流程 action 运行前返回 `CF_EXEC_012`。
- 嵌套流程调用超过配置深度时，在子流程 action 运行前返回 `CF_EXEC_013`；
- 解析后的流程调用图包含非法目标、权限边界不匹配或环时，在子流程 action 运行前返回 `CF_EXEC_014`。

两种结果都不会暴露 mapper 异常或原始 application object。

## 执行 HTTP 契约

Workbench Server 与开发 preview mock 使用可判别执行响应。流程结果以 HTTP 200 返回：

```json
{
  "success": false,
  "message": "Flow execution failed",
  "errorCode": "CF_EXEC_004",
  "error": "Process execution failed",
  "traceId": "4a04d9f8d8bb4d6fb2ae1e577d99dfe2",
  "invocationId": "inv-...",
  "modelType": "TBBPM",
  "sourceDigest": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "durationMs": 12,
  "routing": {
    "namespace": "default",
    "requestedAlias": "production",
    "effectiveVersion": "2026.07.1",
    "alias": "production",
    "routeRevision": 8,
    "target": "STABLE"
  }
}
```

成功响应使用 `success: true` 与 `result`；失败响应必须包含非空 `errorCode` 和 `error`。Workbench 同时在 TypeScript
编译期和运行期校验该区别。

请求格式错误、认证失败、容量拒绝、上游不可用等传输失败使用非 2xx 状态码与 RFC 9457 Problem Details：

```json
{
  "type": "urn:compileflow:problem:invalid-request",
  "title": "Invalid request",
  "status": 400,
  "detail": "request body is required",
  "instance": "/api/executions/preview",
  "code": "INVALID_REQUEST"
}
```

Workbench Server 与开发 mock 在共享 preview 边界使用相同的 `application/problem+json` 形态。生产边缘可以产生自己的传输错误，但必须保持非
2xx，不能伪装成 Engine 结果。大写 `code` 扩展是稳定的程序分支依据，
`detail` 是针对本次失败且可安全展示的文本。

部署命令失败使用非 2xx Problem Details，在 `code` 中返回稳定 `DeploymentErrorCode`、固定且调用方安全的 detail，以及经过验证的
流程上下文。HTTP 适配层绝不复制 `DeploymentException` 的 message、cause、SQL 诊断、传输细节或凭据。客户端按 `code`
分支；详细诊断只保留在受保护的服务端日志中。

## 重试规则

- 浏览器绝不自动重放同步执行 POST。
- 能控制重放的调用方可以在有界收敛期限内重试 `CF_EXEC_011` 或 `CF_EXEC_012`，因为这两个 code 不会在 process action
  已经开始后产生。重试耗尽后必须诊断 route/runtime，不能进入无界循环。
- `CF_EXEC_013` 要求修正调用图或深度预算；等待后重放相同请求无法解决。
- `CF_EXEC_014` 要求修正解析后的调用目标、权限边界或环；等待后重放相同请求无法解决。
- 持久化异步调用默认只尝试一次。
- `maxAttempts > 1` 是对 at-least-once 投递的显式选择。
- 稳定 `invocationId` 提供关联能力和异步提交去重：相同请求返回已有执行，同一 ID 对应不同请求则产生冲突。这仍不能让任意流程副作用变成
  exactly-once。
- 所有可能被重试且会产生副作用的 action，都必须使用稳定业务键实现幂等。
- Workbench 在持久化异步调用前完成 Alias admission，随后只保存选中的 exact version 与有界归因。Routing key 与
  attributes 不进入持久化状态；调用方未提供 `routingKey` 时，以持久化 invocation ID 作为 cohort key。
- 源格式合法但超出 CompileFlow 共享 Process 语义的构造使用 `CF_VALIDATION_006`；目标实现能力不足仍使用
  `CF_VALIDATION_005`。

未知 worker 或边界异常在持久化或 HTTP 暴露前会被归一化为稳定诊断。原始异常消息只应进入显式受保护的诊断工具，绝不能成为默认公共契约。
