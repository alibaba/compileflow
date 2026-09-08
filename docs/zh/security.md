# 安全指南

CompileFlow 会把流程定义编译成 Java 代码，并在宿主 JVM 内执行。请把每一份流程定义都当成可执行代码处理。

[威胁模型](threat-model.md)说明需要保护的资产、参与者、信任边界、防护措施和部署环境中的剩余风险；本文给出相应的配置与运维要求。

## 安全边界

CompileFlow 适用于来源可信、已经授权的流程定义。它不是用于执行任意用户提交 XML、内联 Java、脚本、Spring Bean 调用或部署请求的沙箱。

认证、授权、租户隔离、审批和审计应由接入方应用或运维平台负责。

## 内置保护

### XML 解析加固

共享 XML 流式解析器在解析 BPMN/TBBPM 前会启用安全处理，并关闭 DTD、外部实体和外部 Schema 访问。如果当前 JAXP 实现无法应用任何一项强制安全属性，引擎配置会直接失败，不会降级到更弱的解析方式。

内联定义和类路径定义统一受 `compileflow.engine.definition.max-size` 限制，默认 4 MiB。加载器只读取一次内容并保存为有界的不可变字节快照，Schema 校验和模型解析使用完全相同的字节。应用类加载器返回的已知网络 URL 会被拒绝。

嵌入式引擎不会通过 URL 获取流程定义。远程制品应由应用或部署解析器获取，并在交给编译器前完成认证、网络访问控制、超时、大小限制和摘要校验。

相关回归测试位于：

```text
compileflow-bpmn/src/test/java/com/alibaba/compileflow/engine/bpmn/BpmnModelReaderTest.java
```

### Java 标识符规范化

生成 Java 包名、类名和引擎辅助方法名时会通过 `JavaIdentifiers` 与 `GeneratedProcessNames` 规范化。流程变量名则在语义校验阶段验证为合法且非保留的 Java 标识符，并在生成源码中保持原名，以维持模型、表达式、源码映射与运行状态的一致性。这些约束可避免非法标识符并降低生成代码命名注入风险。
编译器写入磁盘前会校验 Java 全限定名并规范化路径，再将生成源码和 class 文件写入配置的编译目录。内存编译结果返回 class 字节时会创建防御性副本。

### 服务端认证

`compileflow-workbench-server` 支持通过 `compileflow.workbench.server.authentication.api-key` 或
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY` 配置 `X-API-Key` 认证。该密钥必须映射到明确的
`compileflow.workbench.server.authentication.service-principal`，控制面变更以这个稳定服务主体记录操作者。

认证默认使用 `API_KEY`，密钥缺失或强度不足都会导致启动失败。只有显式启用 `dev` 或 `test` 配置环境，且没有同时启用 `prod` 时，才能使用 `DISABLED`。Vite 会把所有 `VITE_*` 值暴露给浏览器，因此 Web 配置不接受任何凭据。生产环境应由可信网关完成用户认证和授权，删除客户端提供的内部请求头，并只在受保护的上游链路中注入 Workbench Server 私有凭据。

一把共享服务端密钥只能代表一个服务主体，不能证明具体终端用户身份。不要接受未经签名验证的浏览器操作者请求头；需要按用户审计时，应增加能够验证并传递用户身份的可信认证机制。

### 受信任的草稿执行

`POST /api/executions/preview` 会使用 Workbench Server 的权限编译并执行提交的流程定义。该接口不是沙箱，预检通过也不代表执行不会产生副作用。接口默认不注册，只有显式设置
`compileflow.workbench.server.preview-execution.enabled=true` 时才注册。

生产环境应保持关闭；确需使用时，身份网关必须只向受信任流程作者授予独立的草稿执行权限。服务端应使用
最小文件系统、网络、数据库和容器权限，并且只暴露已经授权、职责单一的 Spring 动作 Bean。只需要校验时必须调用预检接口，不能调用草稿执行接口。

`compileflow-workbench-server` 使用恒定时间摘要比较已配置的 API 密钥。只有
`/actuator/health`、`/actuator/health/liveness` 与 `/actuator/health/readiness`
可匿名访问；不要用宽泛路径规则暴露其他 Actuator、部署或控制接口。

不要信任浏览器提供的 `X-API-Key`、内部身份、转发信息或客户端 IP 请求头。生产网关必须先删除这些值，再加入自己的服务凭据。内部代理连接错误只应写入受保护日志，浏览器响应应使用安全的 Problem Detail 或通用网关错误。

## 生产环境要求

### 使用可信流程来源

优先使用类路径、受控代码仓库或受控数据库来源：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.BPMN, "order.process", "flows/order.bpmn");
engine.runtime().warmUp(definition);
```

避免直接执行终端用户提交的原始 XML。确实需要接收外部内容时，部署前必须完成验证和授权：

```java
ProcessDefinition definition = ProcessDefinition.inline(ProcessModelType.TBBPM, "order.process", xmlContent);
ProcessPreflightOptions options = ProcessPreflightOptions.strict();
ProcessPreflightReport report = engine.tooling().preflight(definition, options);
if (report.getOverallStatus() == ProcessPreflightReport.OverallStatus.FAIL) {
    String reason = report.getItems().stream()
            .filter(item -> item.getStatus() != ProcessPreflightReport.ItemStatus.PASS)
            .map(ProcessPreflightReport.Item::getMessage)
            .findFirst()
            .orElse("Unknown preflight failure");
    throw new SecurityException("Flow preflight failed: " + reason);
}
```

### 限制可执行动作

Java 动作、Java 代码和 Spring Bean 动作会以宿主应用权限执行。核心模块内置的 QLExpress 4 执行器固定使用
`ISOLATED`、一秒超时、数组单维长度上限和固定安全函数集。编译后的 QL 程序由加载它的确定流程运行时持有，
不存在跨存储实现共享的表达式缓存。内置 `qlexpress` 不提供宿主访问开关；需要不同函数或访问策略时，必须使用新的脚本语言名称注册，并自行负责该语言的安全和持久化语义。

Java 动作会生成直接的 Java 构造和调用代码。类名和方法名在生成前校验，目标类必须提供生成代码可访问的 `public` 无参构造器；引擎不会使用 `private` 反射或依赖注入作为回退方式。组件需要注入依赖时，应使用 Spring Bean 动作。

Java 代码节点包含一段方法体，CompileFlow 会将其放入生成的类型包装代码，并使用 `javac --release 17` 编译。核心模块默认注册 Java 执行器。声明的输入和输出只能使用 Java 平台类型。编译器类路径为空，因此流程中的代码不会意外引用宿主应用的 JAR。这项限制减少了意外暴露，但不会限制代码的执行权限：Java 代码仍是在进程内运行的可信代码，不是安全沙箱。不可信代码必须放入具备操作系统或容器隔离能力的独立执行环境。

Spring Bean 动作默认不能访问任何组件。只应通过 `compileflow.engine.components.allowed-beans` 暴露已经授权的具体 Bean
名，或提供一个自定义 `ProcessComponentResolver`，两者不能同时配置。组件解析失败会使执行失败，不会根据 XML 中声明的 class
自动实例化。Bean 允许列表不是方法级沙箱，因此应只暴露职责单一的适配器或接口。

流程变量默认值始终作为数据字面量处理，并在生成 Java 源码前严格解析；`@` 没有特殊语义。非法字面量和不支持的对象默认值会在
预检阶段失败，不会自动变成 `null` 或未经校验的源码片段。

运行时类型转换同样严格且不依赖区域设置：整数收窄必须无损，时间文本使用 ISO-8601 且不读取默认
时区，转换异常不会包含来源值。完整契约见[流程数据类型与转换](type-system.md)。

自定义脚本语言不属于 Core 内置执行器。通过 `ScriptExecutor` 显式注册后，其安全、超时、缓存、ClassLoader 和生命周期策略由应用负责。
生产环境应：

- 只允许来源可信且已经授权的流程定义上线。
- 将任何包含内建 QL 或 Java 脚本的流程定义视为可执行输入，只允许已经授权的定义上线。
- 除非已经授权的 Spring 动作确实需要，否则保持 `compileflow.engine.components.allowed-beans` 为空。
- 通过职责单一的 Spring 适配器或解析器限制流程可调用的方法。
- 优先暴露职责单一的服务接口，而不是范围宽泛的业务服务。
- 使用最小权限运行宿主应用、容器、数据库账号和网络访问。

### 保护部署与 Operate API

- 生产环境必须同时配置 `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY` 与
  `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL`。
- API 应通过 HTTPS 或可信网关暴露。
- 不要把 Actuator、部署 API 或控制 API 暴露到公网。
- Workbench Server 与所选数据库应部署在可信边界后的私有网络中。
- 绝不将服务端 API 密钥发送给浏览器；CORS 与普通入口路由不是认证机制。
- 网关必须删除客户端提供的 `X-API-Key`、内部身份和不可信转发请求头，只在用户授权成功后注入自己的 Workbench Server
  服务凭据。

### 隔离租户与环境

- 嵌入式引擎和 Deploy 的命名空间只提供逻辑作用域，不提供授权。Workbench 只支持 `default`；需要租户隔离时应使用独立部署。
- 在发布、创建或更新灰度、全量切换、中止、回滚、执行和查看等操作前完成授权。
- 强隔离场景使用独立数据库或数据库行级隔离。
- 不要把 namespace 字符串本身当作授权机制。

### 控制资源暴露

- 对执行入口设置限流和请求大小限制。
- 为持久化异步调用队列、工作节点数量和租约时长设置上限。
- 监控编译耗时、执行耗时、内存占用和失败率。
- 根据生产流程定义的实际大小设置 `compileflow.engine.definition.max-size`；提高它会同步放大每个并发解析任务可占用的内存。
- 部署前拒绝结构非法的流程定义。
- 在 Workbench 中，把流程表达式视为用户输入。客户端模拟必须使用白名单表达式解释器，不使用 `eval` 或
  `new Function`。

### 审计敏感操作

记录以下操作的操作者、时间、租户/环境和目标对象：

- 流程创建与更新。
- 预检、发布、创建或更新灰度、全量切换、中止、回滚和删除。
- 真实执行请求。
- API 密钥与生产配置变更。

审计日志应进入追加写或集中化日志系统。

## 漏洞报告

请通过 [GitHub Security Advisories](https://github.com/alibaba/compileflow/security/advisories/new) 私密报告漏洞。

不要为安全漏洞创建公开 GitHub issue。

## 参考

- [OWASP XXE Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
- [OWASP SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [CompileFlow 资源管理指南](resource-management.md)
