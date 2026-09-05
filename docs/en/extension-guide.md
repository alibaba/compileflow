# CompileFlow Extension Guide

CompileFlow extension contracts live under `com.alibaba.compileflow.engine.spi`. Register an individual capability when
only one engine needs it; use `ProcessEnginePlugin` to package several related capabilities.

Extensions reach the configuration through a classpath plugin, a direct builder call, or a Spring bean.
`ProcessEngineConfig.Builder.build()` validates and freezes the result. There is no global mutable registry, and the
extension set cannot change after the engine starts.

The application or dependency-injection container owns supplied extension instances.

## Package layout

| Package                    | Contents                                                                                                 |
|----------------------------|----------------------------------------------------------------------------------------------------------|
| `engine`                   | Engine entry points and shared value types                                                               |
| `engine.config`            | Immutable configuration and extension registration                                                       |
| `engine.spi`               | `ProcessEngineProvider`, `ProcessEnginePlugin`, `ProcessEnginePluginContext`, `ProcessComponentResolver` |
| `engine.spi.event`         | `ProcessEvent`, `ProcessEventListener`                                                                   |
| `engine.spi.execution`     | `RetryPolicy`, `FailureHandler`, `ProcessContextPropagator`, and execution context values                |
| `engine.spi.script`        | `ScriptExecutor`                                                                                         |
| `engine.spi.observability` | `TraceIdProvider`                                                                                        |
| `engine.spi.routing`       | Alias route authority, route-bound targeting configuration, and named targeting policies                       |

## Extension Points

| Capability                 | Config surface                         | Semantics                                                                            |
|----------------------------|----------------------------------------|--------------------------------------------------------------------------------------|
| `ProcessEventListener`     | `builder.eventListener(...)`           | Ordered, predicate-filtered fan-out; one listener failure is logged and isolated     |
| `TraceIdProvider`          | `builder.traceIdProvider(...)`         | Supplies execution trace IDs; falls back to MDC `traceId`, then a random ID          |
| `ProcessComponentResolver` | `builder.componentResolver(...)`       | Resolves application components referenced by generated process code                 |
| `ProcessContextPropagator` | `builder.contextPropagator(...)`       | Carries application ambient context across engine-owned thread boundaries            |
| `ProcessAliasRouteSource`  | `builder.aliasRouteSource(...)`        | Supplies the engine's single serving-ready Alias authority; never plugin-contributed |
| `ProcessAliasTargetingPolicy` | `builder.aliasTargetingPolicy(...)` | Named route-bound override; empty delegates to fixed percentage selection            |
| `ScriptExecutor`           | `builder.scriptExecutor(...)`          | Explicit-script-action language capability; each name must be unique                 |
| `RetryPolicy`              | `builder.retryPolicy(name, ...)`       | Name-keyed exception predicate referenced by invocation policy `retryOn`             |
| `FailureHandler`           | `builder.failureHandler(name, ...)`    | Name-keyed terminal decision referenced by invocation policy `onFailure`             |
| `ProcessEnginePlugin`      | `builder.plugin(...)` or ServiceLoader | Groups listeners and named script, targeting, retry, and failure capabilities         |

Retry policies and failure handlers are trusted, synchronous execution collaborators. They must be thread-safe,
deterministic, non-blocking, and return a valid result. CompileFlow resolves every collaborator that can participate in
the configured invocation policy before invoking the action, so an unknown identifier fails before application side effects.
A collaborator that throws, or a failure handler that returns `null`, is reported as `CF_CONFIG_003`. Failure handlers
must not perform reliable external delivery; use `ProcessEventListener` for best-effort telemetry and Durable Outbox for
reliable integration.

QLExpress 4 and trusted Java Code are the built-in script languages. QL cannot call host-object methods by default;
Java Code runs with host-JVM privileges and is not a sandbox. Additional script engines are explicit `ScriptExecutor`
extensions. A stateful executor needs bounded caches, the intended class loader, and cleanup managed by its application
or container.

`ScriptExecutor.name()` must already be a 1-256 character lowercase kebab-case key; Core never rewrites it. Only
Script Tasks reference that key. TBBPM `scriptTask` uses
`<action type="script" language="language-name">`; BPMN uses
`<scriptTask scriptFormat="language-name">`. Unknown languages fail during code generation or preflight,
before execution. TBBPM declares Durable execution on its nested Action; BPMN declares
`cf:execution="replayable|effect"` directly on `scriptTask`. The Kernel does not own
historical Provider compatibility. Exclusive-gateway, While, Timer, and transition expressions remain generated Java source and do not use
`ScriptExecutor`. Script-action source and variable names are escaped as Java literals in generated runtime code.

Compiler, parser, graph-analysis, and format dispatch types are internal. The
[Supported Surfaces](../architecture/06-SUPPORTED_SURFACES.en.md) page lists the public contract.

`ProcessEngineProvider` is the bootstrap SPI used by `ProcessEngineFactory`. Missing or ambiguous providers fail engine
creation. Because `ProcessModelType` is closed, a provider can replace an implementation of a known format but cannot
add a format. In Spring, declare a `ProcessEngine` bean to replace the complete engine; auto-configuration then backs
off.

An engine has exactly one explicitly configured `ProcessAliasRouteSource`. It returns complete, serving-ready immutable
routes and must not fall back to another authority. It is deliberately excluded from plugin aggregation so construction
cannot silently select between multiple route authorities.

A `ProcessAliasTargetingPolicy` is registered by stable lowercase kebab-case name and remains inert unless an
authoritative route explicitly references that name through `AliasTargeting`. It receives the authorized stable and
candidate versions, immutable route parameters, and only the routing key and immutable string attributes explicitly
supplied through `ProcessExecutionOptions`; candidate weight is intentionally absent. It may force `STABLE` or
`CANDIDATE`, or return empty to use CompileFlow's fixed deterministic percentage selector. There is no public
percentage-selection SPI.

Routing inputs never enter process variables, and a policy cannot inspect unrelated business inputs. Admission reads
the route source once on the normal path. Only an exact-runtime handoff miss may re-read and retry admission once; after
runtime handoff, that invocation remains pinned. A missing named policy prevents the route from becoming locally ready.
A registered policy returning a null
`Optional` or throwing at runtime selects stable with `TARGETING_ERROR` attribution; it does not enter percentage
routing. Policies must be deterministic, thread-safe, bounded, and free of blocking remote I/O. Routing keys and
attributes are potentially sensitive, admission-only inputs and must never be logged or persisted.

## Explicit Configuration

```java
ProcessComponentResolver components = new ProcessComponentResolver() {
    @Override
    public <T> T resolve(String name, Class<T> requiredType) {
        return serviceLocator.lookup(name, requiredType);
    }
};

ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
        .eventListener(new MetricsListener(meterRegistry))
        .traceIdProvider(TraceIdProvider.random())
        .componentResolver(components)
        .scriptExecutor(new AviatorScriptExecutor())
        .retryPolicy("optimisticConflict", error -> error instanceof OptimisticLockException)
        .failureHandler("continueOptional", context -> FailureResolution.CONTINUE_PROCESS)
        .build();

ProcessEngine engine = ProcessEngineFactory.create(config);
```

Extension instances must be thread-safe: one immutable configuration can create multiple engines, and an engine may call
an extension concurrently. `engine.close()` does not close supplied collaborators.

Custom retry-policy and failure-handler names are trimmed, case-sensitive identifiers. They must contain 1 to 256
characters and no ISO control characters. The built-in names `never`, `transient`, `always`, `propagate`, and
`continue` are reserved case-insensitively. A missing custom name, a thrown policy/handler exception, or a `null`
failure resolution fails execution; CompileFlow does not silently substitute a built-in behavior.

### Application components and actions

`ProcessComponentResolver` is the name-and-type resolution authority. Resolve only explicitly exposed names; missing
or incompatible components fail resolution and never cause the engine to instantiate the class declared by the flow:

```java
OrderFlowActions actions = new OrderFlowActions(orderService);
ProcessComponentResolver resolver = new ProcessComponentResolver() {
    @Override
    public <T> T resolve(String name, Class<T> requiredType) {
        if (!"orderFlowActions".equals(name)) {
            throw new IllegalArgumentException("Unknown process component: " + name);
        }
        return requiredType.cast(actions);
    }
};
```

A Java action uses direct `new Type().method(...)` generated source and therefore requires an accessible class with a
public no-argument constructor. It does not use dependency injection or private reflection. Use a Spring bean action for
injected components, expose an exact name through `compileflow.engine.components.allowed-beans`, and keep the bean
surface narrow because the allowlist controls bean reachability rather than method-level authorization.

`ProcessContextPropagator` captures opaque application context on the submitting thread, opens it on engine-owned
action-timeout, parallel, and event-delivery threads, then restores the worker's previous context. Capture and open
failures at action or parallel boundaries fail the affected invocation; best-effort event delivery drops the event. This
context is never persisted or restored by Durable execution. When Micrometer
Context Propagation is present, Spring Boot supplies this adapter automatically; registered Micrometer
`ThreadLocalAccessor` implementations determine what is carried. One custom `ProcessContextPropagator` bean replaces
that default.

## Classpath Plugins

Implement `ProcessEnginePlugin` and register any combination of capabilities:

```java
public final class AviatorPlugin implements ProcessEnginePlugin {
    @Override
    public void apply(ProcessEnginePluginContext context) {
        if (context.getModelType() == ProcessModelType.TBBPM) {
            context.scriptExecutor(new AviatorScriptExecutor());
            context.eventListener(new AviatorCompilationListener());
            context.retryPolicy("aviatorTransient", new AviatorRetryPolicy());
        }
    }

    @Override
    public String id() {
        return "com.example.aviator";
    }

    @Override
    public int priority() {
        return 100; // lower applies first; duplicate script-language contributions always fail
    }
}
```

Declare it in the standard service file
`META-INF/services/com.alibaba.compileflow.engine.spi.ProcessEnginePlugin`:

```text
com.example.AviatorPlugin
```

Classpath is configuration: adding a dependency that contains a ServiceLoader `ProcessEnginePlugin` can change the
built engine even when application source is unchanged. ServiceLoader plugins are trusted application code. Their constructors and `apply(...)` methods run with the host
process permissions. CompileFlow does not sandbox them, verify signatures, or isolate their dependencies. Disable
discovery when production policy requires an explicit allowlist.

Each configuration uses its configured class loader. Discovered plugins run first, ordered by ascending `priority()` and
then stable `id()`. Explicit plugins run next with the same ordering; direct builder contributions run last. Listeners
append. Duplicate script, targeting, retry, or failure names across plugins fail configuration; priority and bean order
never select a semantic winner. Direct retry and failure registrations may explicitly replace plugin contributions.
Plugin ID and priority are read once during configuration building.

Discovery is disabled by default and can be enabled only when the dependency graph is an approved extension boundary:

```java
ProcessEngineConfig.tbbpmBuilder().discoverPlugins(true).build();
```

A plugin that throws during `apply` fails engine configuration fast; configuration-time errors are never silently
skipped. Runtime listener failures, by contrast, are isolated and logged.

## Invocation Ownership

Extension hosts own discovery, ordering, matching, diagnostics, failure isolation, and instance lifetime. The public SPI
deliberately exposes typed capability contracts rather than a generic invocation utility, so each host keeps its
domain-specific selection and failure semantics local. Plugin implementations contribute capabilities through
`ProcessEnginePluginContext`.

## Spring Boot

Declare typed beans; the starter collects them into the engine configuration automatically:

```java
@Configuration(proxyBeanMethods = false)
class EngineExtensions {

    @Bean
    ProcessEventListener metricsListener(MeterRegistry registry) {
        return event -> registry.counter("flow." + event.getType().name()).increment();
    }

    @Bean
    ScriptExecutor aviatorExecutor() {
        return new AviatorScriptExecutor();
    }

    @Bean
    RetryPolicy optimisticConflict() {
        return error -> error instanceof OptimisticLockException;
    }

    @Bean
    FailureHandler continueOptional() {
        return context -> FailureResolution.CONTINUE_PROCESS;
    }

    @Bean
    TraceIdProvider traceIdProvider() {
        return TraceIdProvider.random();
    }
}
```

- Multiple `ProcessEventListener` beans follow Spring ordering. Exactly one `ScriptExecutor` bean may claim each
  case-insensitive language name; ambiguous beans fail configuration instead of selecting a winner from incidental bean
  order. Plugin precedence is portable across Spring and standalone use:
  `ProcessEnginePlugin.priority()`, then stable `id()`.
- `RetryPolicy` and `FailureHandler` beans are registered under their exact Spring bean names. A direct builder or
  Spring bean contribution replaces a plugin contribution with the same name.
- At most one `TraceIdProvider`, one custom `ProcessComponentResolver`, and one `ProcessContextPropagator` bean may exist.
- With Micrometer Context Propagation on the classpath, the starter supplies the context propagator unless the
  application declares one.
- Automatic Spring component access is denied by default. Expose exact bean names with
  `compileflow.engine.components.allowed-beans`; a non-empty allowlist cannot be combined with a custom resolver.
- ServiceLoader plugin discovery is controlled by the property `compileflow.engine.plugins.discovery-enabled`
  (default `false`).

## Event Model

`ProcessEventListener.supports(ProcessEvent)` may reject unrelated immutable lifecycle events before
`onEvent(ProcessEvent)` is called. Both predicate and delivery failures are isolated and logged:

| Event type                                   | Payload highlights                                                           |
|----------------------------------------------|------------------------------------------------------------------------------|
| `ExecutionStarted`                           | process code and invocation ID                                               |
| `ExecutionCompleted` / `ExecutionFailed`     | controlled `ProcessExecution`, operational `ExecutionAttribution`, duration, and typed `ProcessError` on failure |
| `TriggerStarted`                             | process code, invocation ID, and typed `ProcessTrigger`                      |
| `TriggerCompleted` / `TriggerFailed`         | controlled `ProcessExecution`, operational `ExecutionAttribution`, trigger, duration, and typed error on failure |

When `ProcessObservabilityConfig.eventsAsync` is `true` (the default), events dispatch on the engine-owned event
executor, which the engine closes on shutdown. The trace ID is captured on the publishing thread before any async
handoff. Use `event.getType()` for inexpensive filtering and subtype pattern matching for payload access. Event records
cannot carry routing keys, variables, source content, arbitrary metadata, or raw exceptions. Async delivery is
best-effort: events can overlap or arrive out of order, and saturation drops the rejected event instead of running
application listeners on the process caller thread. Listener order within one event remains deterministic.

## Runtime Boundary

Installing, upgrading, or unloading plugin JARs at runtime is not supported. Add a plugin JAR and restart the
application. Flow-definition hot deployment is provided by `compileflow-deploy`. The supported extension boundary is
listed in [Supported Surfaces](../architecture/06-SUPPORTED_SURFACES.en.md).

Service-loaded plugins should be reusable configuration providers and should not create independently closeable
resources. Resource-owning collaborators belong in explicit application configuration or Spring beans, where their owner
can close them deterministically.
