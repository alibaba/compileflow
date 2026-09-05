# ProcessEngine Resource Management

`ProcessEngine` is a long-lived, thread-safe runtime. It owns bounded executors, runtime and compilation caches, and
generated-class loaders. Construct engines at the application composition root, reuse them across requests, and close
them during application shutdown.

## 1. Choose the right scope

Use one engine for each distinct **model type and immutable configuration boundary**. Most applications therefore use
one TBBPM engine. An application that executes both TBBPM and BPMN uses one long-lived engine for each format; engines
do not share mutable caches, executors, or generated class loaders.

Do not construct an engine per request, flow, tenant, or version. Process identity and version routing are request data,
not reasons to create another engine. Create a separate engine only when the application genuinely needs different model
types, class-loader scopes, extension snapshots, or resource limits.

Extension capabilities supplied through the builder or dependency-injection container are application-owned and may be
shared when they are thread-safe. `ProcessEngine.close()` closes only engine-owned resources.

## 2. Spring Boot lifecycle

The starter creates an application-scoped `ProcessEngine` bean and closes it with the application context. Inject and
reuse that bean:

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import org.springframework.stereotype.Service;

@Service
public final class OrderService {

    private final ProcessEngine engine;

    public OrderService(ProcessEngine engine) {
        this.engine = engine;
    }

    public ProcessResult<OrderResponse> process(OrderRequest request) {
        return engine.execute(
                ProcessDefinition.classpath("bpm.order.process", "flows/order.process.bpm"),
                request,
                OrderResponse.class,
                ProcessExecutionOptions.defaults());
    }
}
```

CompileFlow Workbench Server uses an internal `ProcessEngineRegistry` because it hosts both supported model types.
ProcessEngine starter applications do not need that platform-specific registry.

## 3. Plain Java lifecycle

Create the engine with other application infrastructure and give one owner responsibility for closing it. A top-level
`try` block is sufficient for command-line and batch applications:

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessRef;

import java.util.Map;

public final class Application {

    public static void main(String[] args) {
        try (ProcessEngine engine = ProcessEngineFactory.createTbbpm()) {
            engine.execute(
                    ProcessDefinition.classpath("batch.item.process", "flows/batch/item.process.bpm"),
                    Map.of("itemId", "item-1"))
                    .orElseThrow();
        }
    }
}
```

For a long-running non-Spring service, store the engine in the service's composition root and call
`close()` from the host lifecycle callback. A JVM-global mutable registry or per-request static factory is unnecessary.

Temporary engines are appropriate in isolated tests and short-lived tools. Use
`try-with-resources` so exceptional paths also close them.

## 4. Capacity and shutdown

Executor and runtime residency defaults are bounded. Tune `ProcessExecutorConfig`,
`ProcessEngineConfig.maxResidentRuntimes`, and
`ProcessEngineConfig.runtimeLoadTimeout` from measured load; do not derive thread counts from the number of flows or tenants. Queue
saturation fails visibly instead of allocating unbounded work.

`close()` immediately rejects new public operations and lets admitted work drain. Operation draining and orderly
shutdown of engine-owned executors share `shutdown.grace-period` (10 seconds by default), rather than restarting the
budget at every layer. The close path scans stable non-negative per-thread counter stripes every 50ms. Operation
completion updates only its assigned stripe: it takes no lock, sends no shutdown notification, and does not contend on
one global counter. When the grace period expires, forced cleanup clears engine-owned caches and interrupts
remaining executor work within `shutdown.force-period`. Async callbacks that re-enter after forced cleanup begins fail
as closed. If a worker still ignores interruption after both budgets, cleanup continues and `close()` reports an
`IllegalStateException` rather than claiming success. Java cannot safely kill a caller thread or code that ignores
interruption. Stop admission and drain in-flight calls at the host boundary before closing the engine.

## 5. Observe ownership

Monitor:

- threads with the `compileflow-engine-<id>-` prefix;
- compilation and execution queue saturation or rejection;
- runtime-cache size and eviction behavior;
- shutdown duration and failed close operations;
- heap or class-loader growth after versions are released.

The Spring integration exposes bounded engine gauges through Micrometer when a `MeterRegistry` is available. See
the [Monitoring Guide](monitoring.md) and [Configuration Guide](configuration.md) for the supported metrics and capacity
settings.
