# Performance and Scalability Guide

The recommendations below apply to CompileFlow's high-QPS runtime hot path (`execute`/`trigger`). For operational
procedures and component wiring, see the [Operations playbook](operations-playbook.md) and
[hot-deploy integration](hot-deploy-integration.md).

## Goals

- Minimize per-request overhead on the hot path
- Avoid cold-start spikes and thundering herd on repository/compile
- Keep behavior observable and predictable under load

## Hot-Path Principles

- For a high-QPS path managed by CompileFlow Deploy, use a published `ProcessRef.Version` or `ProcessRef.Alias`.
  Direct `ProcessDefinition` execution is also supported in production, but it resolves source bytes before exact cache
  matching. Keep the source immutable and measure that cost at the expected request rate.
- If the caller already knows the version (replay/job), set it explicitly to bypass version selection.
- Keep targeting policies pure and fast: no remote I/O, no large allocations, and DEBUG-only hot-path logs.

## Transaction Workloads

Share one long-lived, prewarmed engine across concurrent request threads. Untimed serial actions execute on the caller
thread in both runtime modes; a gateway selecting only one branch also executes directly. Explicit action timeouts use
the action-timeout executor, and gateways selecting multiple concurrent branches use the parallel executor. These helper
executors do not limit the number of untimed serial process invocations. A blocking flow must enter through a host thread
that permits blocking, rather than a network event loop.

| Setting                    | Default and sizing decision                                                                                                                                       |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Runtime mode               | Keep `COMPILED` for the transaction hot path; `INTERPRETED` remains supported.                                                                                    |
| Action timeout and retries | No implicit Engine timeout and one attempt. Configure HTTP/RPC/JDBC acquisition and operation deadlines at the actual I/O boundary.                               |
| Action-timeout concurrency | `max(4, CPUs)` is a conservative helper limit. Size explicitly when timed actions are common, using their arrival rate, worker occupancy and downstream capacity. |
| Action-timeout pending     | `0`: reject when no worker is available. A measured burst may justify a small explicit queue if its waiting time fits the request budget.                         |
| Runtime loading            | CPU-derived 1..2 concurrent loads, 4 pending, 10s caller wait. Prepare hot versions before admitting traffic; this wait is not a transaction deadline.            |
| Events                     | Asynchronous, 2 concurrent deliveries and 16 pending. Suitable for bounded best-effort listeners; measure drops and listener service time.                        |
| Cancellation               | 2s cooperative drain for timed actions and failed parallel branches. This extra wait can extend return latency beyond the action timeout.                         |
| Shutdown                   | 15s total Engine budget. Stop host admission first and allow the container enough time for host drain and resource shutdown.                                      |

For timed actions, estimate mean occupied workers as `arrival rate * mean worker occupancy in seconds`. Count every timed
attempt, including retries and work still running after cancellation. For example, 1,000 timed attempts/s occupying a worker
for 20ms need 20 workers on average. A candidate limit of 32 must still be checked against the downstream's allocated
concurrency and CPU budget; neither 20 nor 32 guarantees a tail-latency target. If the downstream budget is smaller than the
required occupancy, reduce admitted load, reduce service time, or increase downstream capacity before raising the limit.
Across replicas, all application pools sharing a dependency consume its aggregate capacity.

Queue capacity buys burst tolerance, not sustained throughput. As an illustrative saturated case, 4 workers taking 50ms
each with 32 pending attempts can add about 400ms of waiting at the tail. Actual tails depend on arrival and service-time
variation. Zero pending can reject short scheduling bursts even below average capacity, so compare useful completed
transactions, rejection rate and p99 under steady traffic, bursts and sustained overload. Use offered-load tests whose
arrival schedule does not slow down when the server slows down; a closed-loop throughput test alone hides queue growth.

An imperative Spring transaction is thread-bound. Timed actions and parallel branches must have explicitly designed
transaction boundaries; propagating MDC does not propagate a transaction or make sharing a JDBC connection safe.
An Engine failure can be returned as a failed `ProcessResult`; handle it inside the transactional method, for example with
`orElseThrow()`, using the application's rollback rules. A timeout or interruption does not undo an external side effect.
Retries require idempotency, explicit retry eligibility and a total deadline. Enabling retries inherits policy defaults;
it does not make a transaction safe to replay.

For the underlying concurrency and transaction semantics, see
[JDK queue policies](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
and [Spring transaction boundaries](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/annotation/Transactional.html).
The numerical examples above illustrate capacity calculations, not measured CompileFlow performance.

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

- Size `compileflow.engine.max-resident-runtimes` for the union of hot exact versions, their ProcessCall dependencies,
  and overlapping release/rollback versions. The default 2048 is a count limit, not a preallocation or a byte budget.
  Measure heap and metaspace as well as eviction/reload activity before changing it. Retained runtimes also count toward
  the limit; when all capacity is retained, another load fails rather than evicting an owned runtime.
- Avoid manual invalidations on hot paths.

## Runtime-Load/Eviction Throttling

- Avoid triggering many cold runtime loads at once. Stagger traffic or warm up.
- Eviction removes runtime bindings and releases the cache's strong reference to the generated runtime. The JVM can
  reclaim the generated ClassLoader after active executions and application references are gone. Keep cache sizing
  stable to avoid recompile and class-loading churn.

## Logging & MDC

- Disable MDC propagation for hot paths (default):
    - `compileflow.engine.observability.mdc-propagation-enabled=false`
- Keep per-request overload logs at DEBUG. Alert on executor rejection and event-drop counters. Measure request outcomes
  and latency at the application boundary; best-effort async events cannot provide exact transaction counts.

## Repository Guidance

- Keep route lookup off the request-time database path; update immutable local routing snapshots asynchronously.
- Cache immutable artifact reads only after measurement, key them by `(namespace, code, version)`, and use bounded size
  and expiry. Never cache a route without its authoritative revision.

## Warm Up on Startup

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class FlowWarmup {
    private final ProcessEngine engine;

    FlowWarmup(ProcessEngine engine) {
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmProcessCache() {
        engine.runtime().warmUp(ProcessDefinition.classpath(
                ProcessModelType.TBBPM, "order.rule", "flows/order-rule.bpm"));
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
