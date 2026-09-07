# Advanced Engine Usage

Engine-local warm-up, tooling, class-loader scope, typed results, and exact version execution are covered below. Durable
publication and routing are documented separately in
[Hot Deployment](hot-deploy.md).

## Warm Up Known Definitions

First execution may need parsing, code generation, and compilation. Validate and load critical definitions during
application readiness:

```java
@Component
public final class FlowWarmup {

    private final ProcessEngine engine;

    public FlowWarmup(ProcessEngine engine) {
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        ProcessDefinition definition =
                ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.bpm");
        ProcessPreflightReport report = engine.tooling()
                .preflight(definition, ProcessPreflightOptions.strict());
        if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
            throw new IllegalStateException(
                    "Flow preflight failed: "
                            + report.getItems().stream()
                                    .filter(item -> item.getStatus()
                                            != ProcessPreflightReport.ItemStatus.PASS)
                                    .map(item -> item.getType() + "/" + item.getStatus()
                                            + ": " + item.getMessage())
                                    .toList());
        }
        engine.runtime().warmUp(definition);
    }
}
```

`warmUp` compiles exact content into this Engine's local runtime cache without creating a code or version binding. It is
not durable publication, does not mutate an Alias, and does not prove that another node is ready.

For a published exact version, the deployment runtime uses:

```java
engine.runtime().load(
        ProcessRef.version("default", "order.process", "2026-07-25.1"),
        ProcessDefinition.inline(ProcessModelType.TBBPM, "order.process", xml));
```

Application code normally lets the deployment runtime own this exact-version lifecycle.

## Generate Java Source

`ProcessToolingService` generates the Java source for an explicit definition without executing it:

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.bpm");
String javaSource = engine.tooling().generateJavaCode(definition);
```

Generated source may expose business rules and action calls. Treat it as sensitive diagnostic output. Parser AST classes
remain implementation details.

## Use A Controlled Class Loader

Generated flow classes resolve application actions through the Engine's configured parent class loader:

```java
try (URLClassLoader applicationLoader = new URLClassLoader(
        new URL[] {Path.of("/opt/application/actions.jar").toUri().toURL()},
        FlowWarmup.class.getClassLoader())) {

    ProcessEngineConfig config = ProcessEngineConfig.builder()
            .classLoader(applicationLoader)
            .build();
    try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
        engine.execute(definition, variables).orElseThrow();
    }
}
```

The class loader is part of local `ProcessRuntimeIdentity` identity. Do not mutate its effective classpath after Engine
construction. The application owns and closes an externally supplied class loader; the Engine closes only resources it
owns.

## Map Typed Input And Output

Typed execution uses the configured `ProcessDataMapper` around the canonical variable-map pipeline:

```java
ProcessResult<OrderResponse> result = engine.execute(
        definition,
        request,
        OrderResponse.class,
        ProcessExecutionOptions.defaults());
```

Input conversion failure returns `CF_EXEC_010` before execution starts. Output conversion failure returns `CF_EXEC_009`
after the process has completed and does not roll back action side effects.

Inject a custom mapper only when its conversion and failure semantics are deterministic and thread-safe:

```java
ProcessEngineConfig config = ProcessEngineConfig.builder()
        .dataMapper(customMapper)
        .build();
```

## Handle Results

```java
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, variables);

if (result.isFailure()) {
    ProcessError error = result.getError();
    logger.warn(
            "Process failed: invocationId={}, errorCode={}",
            result.getExecution().getInvocationId(),
            error.getCode());
    return;
}

Map<String, Object> output = result.getOutput();
```

Use the stable error code for branching. Do not parse the human message, and do not log raw variables, source content,
routing keys, or arbitrary error context.

`map`, `orElse`, `orElseGet`, and `orElseThrow` operate on the immutable result; they do not retry or change the
original process execution.

## Execute A Published Version Or Alias

```java
ProcessRef.Version exact =
        ProcessRef.version("default", "order.process", "2026-07-25.1");

ProcessRef.Alias production =
        ProcessRef.alias("default", "order.process", "production");

ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .aliasRouting(new AliasRoutingOptions(customerId))
        .build();

ProcessResult<OrderResponse> result = engine.execute(
        production,
        request,
        OrderResponse.class,
        options);
```

An exact version must be locally installed. An Alias reads the local-ready stable/candidate route and fails closed if
the selected version is unavailable. There is no previous-version fallback.

## Extend The Engine

Use the extension interfaces in `compileflow-api`:

- `ProcessEnginePlugin` for grouped configuration contributions;
- `ProcessEventListener` for events;
- `ScriptExecutor` for script languages;
- `RetryPolicy` and `FailureHandler` for named job policies;
- named `ProcessAliasTargetingPolicy` values for route-bound enterprise targeting overrides.

Capabilities are registered while building the configuration and remain fixed for the engine lifetime. Runtime plugin
replacement is not supported.

See [Extension Guide](extension-guide.md), [API Reference](api-reference.md), and
[Configuration](configuration.md).
