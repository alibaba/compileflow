# 执行失败模型

CompileFlow 明确区分流程结果、边界异常与 HTTP 传输异常。三者不能混为一谈：调用方必须知道流程是尚未开始、执行中失败，还是已经完成但响应转换失败。

## 引擎 API

`execute(...)` 和 `trigger(...)` 对执行管线中的失败返回 `ProcessResult<T>`。每个结果都包含
`ProcessExecution` 执行信息，并且只包含一种结果：

- 成功：包含 `output`，不包含错误；
- 失败：包含 `ProcessError`，其中有稳定的 `code` 和可安全展示的 `message`。

`ProcessExecution` 只暴露跟踪 ID、调用 ID、命名空间、流程编码、可选的已发布版本，以及开始和完成时间。终态
`ProcessEvent` 通过独立的 `ExecutionAttribution` 携带父调用、调用深度、模型类型、源码摘要和准入别名。
两者都不包含路由键、流程变量、源码内容、任意元数据和原始异常。

调用方应根据 `isSuccess()` 或 `getError().getCode()` 分支，不要解析 `message` 文本。无参
`orElseThrow()` 会抛出 `ProcessExecutionException`，并保留相同的 `ProcessError` 与内存中的
`ProcessExecution`。

非法 Java API 参数会在调用被接受前直接抛出。类型化适配器的失败通过结果返回，便于调用方判断是否可以重试：

- 非法流程定义（包括 XSD 违规）是归类为 `CF_VALIDATION_002` 的建模错误。执行管线通过 `ProcessResult`
  返回失败；`tooling().preflight(...)` 返回含阶段诊断的失败报告，而不是抛出该定义校验失败。非法 API 参数、
  生命周期误用以及基础设施中断或拒绝仍可能抛异常；
- input mapping 在流程开始前返回 `CF_EXEC_010`；
- output mapping 在流程完成后返回 `CF_EXEC_009`，并明确提示副作用可能已经发生；
- Alias 路由缺失或并发变化时，会在任何流程动作运行前返回 `CF_EXEC_011`；
- 选定的运行时在本节点不可用时，会在任何流程动作运行前返回 `CF_EXEC_012`；
- 嵌套流程调用超过配置深度时，会在子流程动作运行前返回 `CF_EXEC_013`；
- 解析后的流程调用图包含非法目标、权限边界不匹配或环时，会在子流程动作运行前返回 `CF_EXEC_014`。

两种结果都不会暴露映射器异常或原始应用对象。

## 执行 HTTP 契约

Workbench Server 与本地开发网关使用可区分的执行响应。流程结果以 HTTP 200 返回：

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

Workbench Server 与本地开发网关使用相同的 `application/problem+json` 格式。生产网关可以返回自己的传输错误，但必须保持非
2xx，不能伪装成引擎结果。大写 `code` 扩展是稳定的程序分支依据，
`detail` 是针对本次失败且可安全展示的文本。

部署命令失败使用非 2xx Problem Details，在 `code` 中返回稳定的 `DeploymentErrorCode`、可安全展示的 `detail`，以及经过验证的
流程上下文。HTTP 适配层不会复制 `DeploymentException` 的消息、原因、SQL 诊断、传输细节或凭据。客户端按 `code`
分支；详细诊断只保留在受保护的服务端日志中。

## 重试规则

- 浏览器绝不自动重放同步执行 POST。
- 能控制重放的调用方可以在有限的收敛时间内重试 `CF_EXEC_011` 或 `CF_EXEC_012`，因为这两个错误不会在流程动作
  已经开始后产生。重试耗尽后应检查路由和运行时，不能无限循环。
- `CF_EXEC_013` 要求修正调用图或深度预算；等待后重放相同请求无法解决。
- `CF_EXEC_014` 要求修正解析后的调用目标、权限边界或环；等待后重放相同请求无法解决。
- 持久化异步调用默认只尝试一次。
- `maxAttempts > 1` 表示显式选择至少一次投递语义。
- 稳定 `invocationId` 提供关联能力和异步提交去重：相同请求返回已有执行，同一 ID 对应不同请求则产生冲突。这仍不能让任意流程副作用变成
  恰好一次语义。
- 所有可能被重试且会产生副作用的流程动作，都必须使用稳定业务键实现幂等。
- Workbench 在持久化异步调用前完成别名准入，随后只保存选定的精确版本和必要执行信息。路由键与
  路由属性不进入持久化状态；调用方未提供 `routingKey` 时，以持久化调用 ID 作为分组键。
- 源格式合法但超出 CompileFlow 共享 Process 语义的构造使用 `CF_VALIDATION_006`；目标实现能力不足仍使用
  `CF_VALIDATION_005`。

来自未知工作节点或边界的异常，在持久化或通过 HTTP 暴露前会转换为稳定诊断。原始异常消息只应进入明确受保护的诊断工具，绝不能成为默认公共契约。
