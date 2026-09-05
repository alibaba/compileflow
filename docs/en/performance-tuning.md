# Performance and Scalability Guide

The recommendations below apply to CompileFlow's high-QPS runtime hot path (`execute`/`trigger`). For day-2 procedures
and component wiring, use the Operations Playbook and Integration Guide.

## Goals

- Minimize per‑request overhead on the hot path
- Avoid cold‑start spikes and thundering herd on repository/compile
- Keep behavior observable and predictable under load

## Hot-Path Principles

- Use a published `ProcessRef.Version` or `ProcessRef.Alias` on the steady-state production hot path. Every
  `ProcessDefinition` variant resolves source bytes before exact cache matching and is intended for development, tooling, or
  lower-frequency explicit execution.
- If the caller already knows the version (replay/job), set it explicitly to bypass version selection.
- Keep targeting policies pure and fast: no remote I/O, no large allocations, and DEBUG-only hot-path logs.

## Alias Target Selection

- Runtime execution constructs `ProcessAliasTargetingContext` only when the serving route names a targeting policy.
  Do not put routing keys or attributes into the process-variable map.
- Keep `ProcessAliasTargetingPolicy` logic bounded and deterministic. Refresh external policy state asynchronously
  outside the hot path.
- In Spring Boot, expose named `ProcessAliasTargetingPolicy` beans. Configure a custom `ProcessAliasRouteSource` only
  when the application owns a different serving-route authority.

### Example: App-level Targeting Policy Bean

```java
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoutingConfiguration {

    @Bean
    ProcessAliasTargetingPolicy tierTargetingPolicy() {
        return new ProcessAliasTargetingPolicy() {
            @Override
            public String name() {
                return "tier-targeting";
            }

            @Override
            public Optional<ProcessAliasTarget> target(ProcessAliasTargetingContext context) {
                return "vip".equals(context.getAttributes().get("tier"))
                        ? Optional.of(ProcessAliasTarget.CANDIDATE)
                        : Optional.empty();
            }
        };
    }
}
```

Notes:

- Keep targeting policies pure and fast; inspect only their immutable context.
- Pass the alias through `ProcessRef.Alias`, and the routing identity and custom policy inputs through immutable
  `ProcessExecutionOptions`; policies never receive unrelated business variables.
- Bind the policy name explicitly in the Alias rollout. A registered but unreferenced policy is inert, and an Alias
  naming an unavailable policy cannot become locally ready.
- Standard canaries use the protocol-defined deterministic percentage selector. Targeting can override or fall through;
  it cannot replace percentage selection.

## Cold-Path Smoothing

- The engine coalesces identical in-flight runtime loads. Tune `compileflow.engine.executor.runtime-load.max-concurrency`
  for active loads and `compileflow.engine.executor.runtime-load.max-pending` for additional loads waiting for a slot;
  set pending to zero when fail-fast admission is preferred.
- Consider seconds-level hotspot caching for immutable `(namespace, code, version)` artifact lookups when measurements
  show repository pressure. Route selection should already read an asynchronously refreshed in-memory snapshot.

## Runtime Cache Sizing

- Increase `compileflow.engine.max-resident-runtimes` to reduce evictions and classloader churn.
- Avoid manual invalidations on hot paths.

## Runtime-Load/Eviction Throttling

- Avoid triggering many cold runtime loads at once. Stagger traffic or warm up.
- Eviction removes runtime bindings and releases the cache's strong reference to the generated runtime. The JVM can
  reclaim the generated ClassLoader after active executions and application references are gone. Keep cache sizing
  stable to avoid recompile and class-loading churn.

## Logging & MDC

- Disable MDC propagation for hot paths (default):
    - `compileflow.engine.observability.mdc-propagation-enabled=false`
- Keep hot-path logs at DEBUG; sample metrics/events when under heavy load.

## Repository Guidance

- Keep route lookup off the request-time database path; update immutable local routing snapshots asynchronously.
- Cache immutable artifact reads only after measurement, key them by `(namespace, code, version)`, and use bounded size
  and expiry. Never cache a route without its authoritative revision.

## Warm Up on Startup

```java
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

class FlowWarmup {
    private final ProcessRuntimeManager admin;

    FlowWarmup(ProcessRuntimeManager admin) {
        this.admin = admin;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmProcessCache() {
        admin.warmUp(ProcessDefinition.classpath(
                "order.rule", "flows/order-rule.bpm"));
    }
}
```

## Checklist

- [ ] Runtime uses `code`/`version` (no inline content)
- [ ] Selectors are pure in-memory and read local snapshots only
- [ ] Compilation workers, queue, and in-flight registry are bounded for the workload
- [ ] Runtime cache sized to reduce evictions
- [ ] MDC disabled; hot logs at DEBUG; metrics sampled
- [ ] Any artifact cache is measured, bounded, and keyed by immutable version
- [ ] Known startup definitions or selected versions are warmed explicitly
