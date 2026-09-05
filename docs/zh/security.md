# 安全指南

CompileFlow 会把流程定义编译成 Java 代码，并在宿主 JVM 内执行。请把每一份流程定义都当成可执行代码处理。

[威胁模型](threat-model.md)记录发布评审所需的资产、参与者、信任边界、缓解措施与部署环境剩余风险；本指南把这些边界落实为
配置和运维要求。

## 安全边界

CompileFlow 适用于受信任、经过评审的流程定义。它不是用于执行任意用户提交 XML、内联 Java、脚本、Spring Bean 调用或部署请求的沙箱。

认证、授权、租户隔离、审批和审计应由接入方应用或运维平台负责。

## 内置保护

### XML 解析加固

共享 XML 流式解析器在解析 BPMN/TBBPM 前会启用安全处理，并关闭 DTD、外部实体和外部 schema 访问。这些安全控制采用
fail-closed：当前 JAXP provider 无法应用任一强制属性时，Engine 配置失败，不会降级到更弱的解析模式。

inline 与 classpath 定义统一受 `compileflow.engine.definition.max-size` 限制，默认 4 MiB。loader
只读取来源一次并固化为有界不可变字节快照，schema 校验和模型解析看到的是同一组字节。应用 ClassLoader 返回的已知网络 URL
协议会被拒绝。

嵌入式 Engine 也不会通过 URL 获取流程定义。远程制品应由应用或部署 resolver 获取，并在内容进入编译器前完成认证、网络白名单、超时、大小限制和摘要校验。

相关回归测试位于：

```text
compileflow-bpmn/src/test/java/com/alibaba/compileflow/engine/bpmn/BpmnModelReaderTest.java
```

### Java 标识符规范化

生成 Java 包名、类名和引擎辅助方法名时会通过 `JavaIdentifiers` 与 `GeneratedProcessNames` 规范化。流程变量名则在语义校验阶段验证为合法且非保留的 Java 标识符，并在生成源码中保持原名，以维持模型、表达式、源码映射与运行状态的一致性。这些约束可避免非法标识符并降低生成代码命名注入风险。
编译器磁盘输出会先校验 Java 全限定名并规范化路径，再把生成的源码和 class 文件解析到配置的编译目录下；内存编译产物对 class
字节暴露防御性快照。

### Server 认证守卫

`compileflow-workbench-server` 支持通过 `compileflow.workbench.server.authentication.api-key` 或
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY` 配置 `X-API-Key` 认证。该 key 必须映射到显式的
`compileflow.workbench.server.authentication.service-principal`，控制面变更以这个稳定服务主体作为审计 actor。

认证默认使用 `API_KEY`，key 缺失或强度不足都会导致启动失败。只有显式启用 `dev` 或 `test` profile 且未同时启用
`prod` 时才接受 `DISABLED`。Vite 会把所有 `VITE_*` 值暴露给浏览器，因此 Web 配置 schema 不接受任何凭据。
生产环境应由具备认证能力的网关完成用户认证与授权，剥离客户端提供的内部 header，并只在受保护的上游链路注入 Workbench
Server 私有凭据。

一把共享 server key 只能代表一个服务主体，不能证明具体终端用户身份。不要接受未经签名验证的浏览器 actor
header；需要逐用户审计时，应增加能够验证并传播用户 principal 的可信认证机制。

### 受信任的草稿执行

`POST /api/executions/preview` 会使用 Workbench Server 权限编译并执行提交的定义。它不是沙箱，preflight
通过也不代表执行没有副作用。该端点默认不存在，只有显式设置
`compileflow.workbench.server.preview-execution.enabled=true` 时才注册。

生产环境应保持关闭；确需使用时，身份网关必须只向受信任流程作者授予独立的草稿执行权限。Server 应使用
最小文件系统、网络、数据库和容器权限，并且只暴露经过审查的窄 Spring action bean。只做校验的工作流必须调用 preflight，不能调用
Preview。

compileflow-workbench-server 使用恒定时间 digest 比较已配置的 API key。只有
`/actuator/health`、`/actuator/health/liveness` 与 `/actuator/health/readiness`
可匿名访问；不要用宽泛路径规则暴露其他 Actuator、部署或控制接口。

不要信任浏览器提供的 `X-API-Key`、内部身份、forwarding 或 client-IP 头。生产网关必须先剥离这些值，再加入自己
的服务凭据。内部代理连接错误只应写入受保护日志，浏览器响应应使用安全 Problem Detail 或通用 gateway 错误。

## 生产环境要求

### 使用可信流程来源

优先使用 classpath、经过评审的仓库或受控数据库来源：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath("order.process", "flows/order.bpmn");
engine.runtime().warmUp(definition);
```

避免直接执行终端用户提交的原始 XML。确实需要接收外部内容时，部署前必须验证和评审：

```java
ProcessDefinition definition = ProcessDefinition.inline("order.process", xmlContent);
ProcessPreflightOptions options = ProcessPreflightOptions.strict();
ProcessPreflightReport report = engine.tooling().preflight(definition, options);
if (report.getOverallStatus() == ProcessPreflightReport.OverallStatus.FAIL) {
    throw new SecurityException("Flow preflight failed for " + report.getCode());
}
```

### 限制可执行动作

Java action、Java Code 和 Spring Bean action 会以宿主应用权限执行。core 内建的 QLExpress 4 执行器固定使用
`ISOLATED`、一秒超时、数组单维长度上限和固定安全函数集。Compiled QL program 由加载它的精确 Process runtime 持有，
不存在 provider-global expression cache。内建 `qlexpress` 不提供宿主访问开关；需要不同函数面或访问策略时，必须注册一个新的
语义语言名，并自行承担安全和历史语义兼容合同。

Java action 会生成直接的 Java 构造与调用代码。类名和方法名在生成前校验，目标类必须提供生成代码可访问的 public
无参构造器；引擎不会回退到 private 反射或依赖注入。组件需要注入依赖时应使用 Spring Bean action。

Java Code 是 method body，CompileFlow 会将其放入生成的 typed wrapper，并用 `javac --release 17` 编译。core 默认注册
内置 Java executor。声明的输入和输出只能使用 Java platform type。编译器 classpath 为空，因此 definition-owned code
不会意外绑定宿主应用的 JAR。这项限制减少了意外暴露，但不限制执行权限：Java Code 仍是可信的进程内计算，不是安全沙箱。
不可信作者代码必须放入具有操作系统或容器隔离边界的 Code Runner。

Spring Bean action 默认拒绝访问组件。仅通过 `compileflow.engine.components.allowed-beans` 暴露经过评审的精确 bean
名，或提供一个自定义 `ProcessComponentResolver`，两者不能同时配置。组件解析失败会使执行失败，不会根据 XML 中声明的 class
自动实例化。bean allowlist 不是方法级沙箱，因此应暴露只包含流程可调用操作的窄 adapter 或接口。

流程变量默认值始终作为数据字面量处理，并在生成 Java 源码前严格解析；`@` 没有特殊语义。非法字面量和不支持的对象默认值会在
preflight 失败，不会静默变成 `null` 或未经校验的源码片段。

运行时类型转换同样严格且不依赖 locale：整数收窄必须无损，时间文本使用 ISO-8601 且不隐式读取默认
时区，转换异常不会包含来源值。完整契约见[流程数据类型与转换](type-system.md)。

Groovy、MVEL 等语言不再是 core 内建执行器。通过 `ScriptExecutor` 显式注册后，其安全、超时、缓存、ClassLoader
和生命周期策略由应用负责。生产环境应：

- 只允许经过评审的流程定义上线。
- 将任何包含内建 QL 或 Java 脚本的流程定义视为可执行输入，只允许经过评审的定义上线。
- 除非经过评审的 Spring action 确实需要，否则保持 `compileflow.engine.components.allowed-beans` 为空。
- 通过窄 Spring adapter 或 resolver 返回接口限制流程可调用的方法。
- 优先暴露窄接口 service adapter，而不是暴露宽泛业务服务。
- 使用最小权限运行宿主应用、容器、数据库账号和网络访问。

### 保护部署与 Operate API

- 生产环境必须同时配置 `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY` 与
  `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL`。
- API 应通过 HTTPS 或可信网关暴露。
- 不要把 Actuator、部署 API 或控制 API 暴露到公网。
- Workbench Server 与 PostgreSQL 应位于可信边缘之后的私有网络。
- 绝不把 Server API key 发给浏览器；CORS 与普通 Ingress 路由不是认证机制。
- 网关必须剥离客户端提供的 `X-API-Key`、内部身份和不可信 forwarding header，只在用户授权成功后注入自己的 Workbench Server
  服务凭据。

### 隔离租户与环境

- 使用 namespace 区分租户或环境。
- 在 publish、rollout 创建/更新/提升/中止、rollback、execute、inspect 等操作前做授权。
- 强隔离场景使用独立数据库或数据库行级隔离。
- 不要把 namespace 字符串本身当作授权机制。

### 控制资源暴露

- 对执行入口设置限流和请求大小限制。
- 为持久化异步调用队列、worker 数量和 lease 时间设置边界。
- 监控编译耗时、执行耗时、内存占用和失败率。
- 把 `compileflow.engine.definition.max-size` 保持在已评审生产定义的合理上限附近；提高它会同步放大每个并发解析任务可占用的内存。
- 除非确有需要，否则保持 file 来源关闭；启用时只配置专用只读目录，不要授权宽泛的应用目录或宿主根目录。
- 部署前拒绝结构非法的流程定义。
- 在 Workbench 中，把流程表达式视为用户输入。客户端模拟必须使用白名单表达式解释器，不使用 `eval` 或
  `new Function`。

### 审计敏感操作

记录以下操作的操作者、时间、租户/环境和目标对象：

- 流程创建与更新。
- preflight、publish、rollout 创建/更新/提升/中止、rollback、delete。
- 真实执行请求。
- API key 与生产配置变更。

审计日志应进入追加写或集中化日志系统。

## 依赖与 CI 卫生

- 对改动模块运行定向 Maven 测试。
- 发布候选版本前运行 `./mvnw checkstyle:check`。
- 在 CI 或发布准备阶段执行依赖漏洞扫描。
- 发布前检查生成的产物与资产清单。

## 漏洞报告

请通过 [GitHub Security Advisories](https://github.com/alibaba/compileflow/security/advisories/new) 私密报告漏洞。

不要为安全漏洞创建公开 GitHub issue。

## 参考

- [OWASP XXE Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
- [OWASP SQL Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
- [CompileFlow 资源管理指南](resource-management.md)
