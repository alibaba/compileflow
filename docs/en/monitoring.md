# CompileFlow Monitoring & Observability Guide

This guide explains how to tap into CompileFlow's event system to monitor engine performance, collect metrics, and gain
deep visibility into your process executions.

## 1. How It Works: SPI and Events

CompileFlow's monitoring capabilities are built on a simple yet powerful event-driven mechanism using Java's **Service
Provider Interface (SPI)**.

1. **`ProcessEventListener` Interface**: The core of the system. You implement this interface to create a "listener"
   that can react to events published by the engine.
2. **Engine Events**: The `ProcessEngine` publishes events at critical lifecycle points, such as when a process starts,
   completes, or fails.
3. **SPI Registration**: You register your custom listener by creating a specific file in your project's
   `META-INF/extensions` directory. The engine discovers and loads any listeners listed in this file at startup.

This decoupled approach allows you to add custom monitoring, logging, and metrics collection without modifying the
engine's core code.

---

## 2. Enabling Observability

To activate the event system, you must first enable it in your configuration.

**`application.yml` (for Spring Boot)**

```yaml
compileflow:
  observability:
    # Master switch to enable all monitoring features (metrics, events, tracing).
    # This must be true for any listeners to be triggered.
    enabled: true
    # For performance-critical applications, ensure events are processed asynchronously.
    events-async: true
```

**Programmatic Configuration**

```java
// Not yet available via programmatic config. Use system properties as a workaround:
// -Dcompileflow.observability.enabled=true
// -Dcompileflow.observability.events-async=true
```

---

## 3. Implementing a Basic Event Listener

Here is a simple example of a listener that logs when a process completes successfully.

**Step 1: Create the Listener Class**

```java
package com.example.listeners;

import com.alibaba.compileflow.engine.core.event.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @ExtensionRealization is optional but recommended for clarity.
// The primary discovery mechanism is the SPI file.
@ExtensionRealization
public class SimpleProcessLogger implements ProcessEventListener<ProcessEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleProcessLogger.class);

    @Override
    public void onEvent(ProcessEvent event) {
        // Use instanceof for type-safe event handling
        if (event instanceof ProcessCoreEvents.ExecutionCompleted) {
            ProcessCoreEvents.ExecutionCompleted completedEvent = (ProcessCoreEvents.ExecutionCompleted) event;
            LOGGER.info("Process [{}] completed in {}ms.",
                completedEvent.getProcessCode(), completedEvent.getContext().getDurationMs());
        }
    }
}
```

**Step 2: Register the Listener via SPI**

Create the following file in your project's resources directory:
`META-INF/extensions/com.alibaba.compileflow.engine.core.event.ProcessEventListener`

Add the fully qualified name of your listener class to this file:

```
com.example.listeners.SimpleProcessLogger
```

Now, when the engine starts, it will automatically discover and register your `SimpleProcessLogger`.

---

## 4. Integrating with Micrometer for Metrics

A more powerful use case is to collect metrics for monitoring systems like Prometheus and Grafana. This example shows
how to integrate with Micrometer.

**Step 1: Create the Micrometer Listener**

```java
package com.example.listeners;

import com.alibaba.compileflow.engine.core.event.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

@ExtensionRealization
public class MicrometerMetricsListener implements ProcessEventListener<ProcessEvent> {

    private final MeterRegistry meterRegistry;

    // In a real application, inject MeterRegistry via your DI framework.
    // For this example, we assume it's passed in.
    public MicrometerMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onEvent(ProcessEvent event) {
        String processCode = event.getProcessCode();
        ProcessEventContext context = event.getContext();

        if (event instanceof ProcessCoreEvents.ExecutionCompleted) {
            Timer.builder("compileflow.execution.duration")
                .description("Process execution duration")
                .tag("process.code", processCode)
                .tag("status", "success")
                .register(meterRegistry)
                .record(context.getDurationMs(), TimeUnit.MILLISECONDS);
        } else if (event instanceof ProcessCoreEvents.ExecutionFailed) {
            Timer.builder("compileflow.execution.duration")
                .description("Process execution duration")
                .tag("process.code", processCode)
                .tag("status", "failure")
                .register(meterRegistry)
                .record(context.getDurationMs(), TimeUnit.MILLISECONDS);
        } else if (event instanceof ProcessCoreEvents.CompilationFailed) {
            Counter.builder("compileflow.compilation.failures")
                .description("Process compilation failures")
                .tag("process.code", processCode)
                .register(meterRegistry)
                .increment();
        }
    }

    // By implementing onEvent with `instanceof`, the `support` and `isAsync`
    // methods are no longer needed, as the base interface provides safe defaults.
}
```

**Step 2: Register the Listener via SPI**

Add your new listener to the SPI file:
`META-INF/extensions/com.alibaba.compileflow.engine.core.event.ProcessEventListener`

```
com.example.listeners.SimpleProcessLogger
com.example.listeners.MicrometerMetricsListener
```

---

## 5. Spring Boot Integration

In a Spring Boot application, you can register your listener as a bean. This allows you to inject other Spring-managed
components (like `MeterRegistry`). Discovery is SPI/annotation based; registering as a bean is for DI support, not for
discovery.

**Step 1: Create the Listener as a Spring Bean**

Modify the `MicrometerMetricsListener` to be a Spring component. **You no longer need the SPI file if you use this
approach.**

```java
package com.example.config;

// ... (imports)
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
// This allows you to enable/disable the listener from application.yml
@ConditionalOnProperty(name = "compileflow.custom-metrics.enabled", havingValue = "true")
public class MicrometerMetricsListener implements ProcessEventListener<ProcessEvent> {

    private final MeterRegistry meterRegistry;

    // Use constructor injection for dependencies.
    @Autowired
    public MicrometerMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // The onEvent method remains the same as the previous example.
    @Override
    public void onEvent(ProcessEvent event) {
        // ... (implementation from above)
    }
}
```

**Step 2: Add Configuration Property**

Now you can control the listener from your `application.yml`:

```yaml
compileflow:
  observability:
    enabled: true # This must be true globally
  custom-metrics:
    enabled: true # This is your custom toggle for this specific listener

management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health"
```

Note on support and isAsync

- You may optionally override `support(ProcessEventExtensionContext context)` to conditionally enable a listener. The
  default implementation returns `true`.
- You may optionally override `isAsync()` to control dispatch mode. The default is `true` (async).

---

## 6. Recommended Metrics & Dashboards

Here are some essential metrics to collect and sample PromQL queries for your Grafana dashboards.

### Key Metrics to Track

- **Execution Duration (Timer)**: `compileflow_execution_duration_seconds`
    - *Why*: Tracks the performance of your processes.
    - *Tags*: `process.code`, `status` (success/failure)
- **Execution Count (Counter from Timer)**: `compileflow_execution_duration_seconds_count`
    - *Why*: Measures the throughput of your processes.
- **Compilation Failures (Counter)**: `compileflow_compilation_failures_total`
    - *Why*: Alerts you to invalid process definitions being deployed.

### Sample PromQL Queries

```promql
# 95th percentile execution time for successful processes, grouped by code
histogram_quantile(0.95, sum(rate(compileflow_execution_duration_seconds_bucket{status="success"}[5m])) by (le, process_code))

# Throughput (executions per second) grouped by process code
sum(rate(compileflow_execution_duration_seconds_count[5m])) by (process_code)

# Error rate percentage over the last 30 minutes
(sum(rate(compileflow_execution_duration_seconds_count{status="failure"}[30m])) by (process_code) / sum(rate(compileflow_execution_duration_seconds_count[30m])) by (process_code)) * 100

# Rate of compilation failures over the last 30 minutes
sum(rate(compileflow_compilation_failures_total[30m]))
```

---

## 7. Best Practices

- **Keep Listeners Fast**: Even though events can be async, listeners should be lightweight. For heavy operations (e.g.,
  writing to a database), hand off the work to a separate, dedicated thread pool.
- **Avoid High Cardinality Tags**: Do not use tags with unbounded values (like `orderId` or `userId`), as this can
  overwhelm your metrics system. Use low-cardinality tags like `process_code`.
- **Handle Errors**: Wrap your listener logic in a `try-catch` block to prevent a faulty listener from disrupting other
  listeners.
- **Combine with Logging**: Use metrics to find *what* is slow, and use structured logs (with a `traceId` from the
  `ProcessEventContext`) to find out *why*.
