# CompileFlow Configuration Guide

Welcome to the comprehensive configuration guide for CompileFlow. This document covers everything you need to know, from
basic setup to advanced performance tuning.

Our philosophy is simple:

- **Simple by default:** For 99% of use cases, you only need to configure a few high-level options.
- **Powerful when needed:** For advanced scenarios, we provide deep, fine-grained control over every aspect of the
  engine.

---

## 1. Configuration Priority

CompileFlow uses a layered configuration model. When a property is set in multiple places, the one with the highest
priority wins.

The priority order is as follows (from highest to lowest):

1. **Programmatic Configuration:** Settings applied directly in code via `ProcessEngineConfig.builder()` have the
   highest priority. They will always override any other configuration source.
2. **Spring Boot (`application.yml`):** Properties defined in your `application.yml` or `application.properties` file
   are the most common and recommended way to configure CompileFlow.
3. **Java System Properties:** Properties passed to the JVM on startup (e.g.,
   `-Dcompileflow.cache.runtime-max-size=5000`).
4. **Internal Defaults:** The engine's built-in default values.

---

## 2. Primary Configuration (for 99% of Users)

For most applications running on Spring Boot, this is all you need. Simply add the following to your `application.yml`:

```yaml
compileflow:
  # [General] The process model type. TBBPM is optimized for enterprise workflows,
  # while BPMN follows international standards.
  # Options: TBBPM, BPMN
  model-type: TBBPM

  # [Performance] The two most critical performance settings.
  executor:
    # Number of threads for compiling flow definitions.
    # Recommended: 1-4. Default: Math.min(2, Math.max(1, CPUs/8)). Increase only if you have many flows to compile at startup.
    compilation-threads: 2
    # Number of threads for executing flows.
    # Recommended: Start with the number of CPU cores and tune based on load. Default: Math.max(4, CPUs).
    execution-threads: 16

  # [Cache] The most important cache setting.
  cache:
    # Maximum number of compiled flows to keep in memory.
    # Higher values improve performance but consume more memory.
    # Recommended: 100-500 (dev), 2000-10000 (prod).
    runtime-max-size: 2000

  # [Observability] Settings for monitoring and metrics.
  observability:
    # Master switch to enable all monitoring features (metrics, events, tracing).
    # Essential for production environments.
    enabled: true
```

---

## 3. Full Property Reference

This table provides a complete list of all available properties, their default values, and their corresponding Java
System Property keys.

### General

| Property (YAML)                            | Type      | Description                               | Default | System Property Key                        |
|--------------------------------------------|-----------|-------------------------------------------|---------|--------------------------------------------|
| `compileflow.model-type`                   | `String`  | Process model type: `TBBPM` or `BPMN`.    | `TBBPM` | N/A                                        |
| `compileflow.parallel-compilation-enabled` | `Boolean` | Enable parallel compilation of processes. | `true`  | `compileflow.compilation.parallel-enabled` |

### Executor

**Note:** Advanced properties like keep-alive times and queue sizes are configurable only via Java System Properties,
not YAML.

| Property (YAML)                            | Type      | Description                                        | Default                              | System Property Key                                   |
|--------------------------------------------|-----------|----------------------------------------------------|--------------------------------------|-------------------------------------------------------|
| `compileflow.executor.compilation-threads` | `Integer` | Number of threads for process compilation.         | `Math.min(2, Math.max(1, CPUs / 8))` | `compileflow.executor.compilation-threads`            |
| `compileflow.executor.execution-threads`   | `Integer` | Number of threads for process execution.           | `Math.max(4, CPUs)`                  | `compileflow.executor.execution-threads`              |
| N/A                                        | `Long`    | Keep-alive time for compilation threads (seconds). | `60`                                 | `compileflow.executor.compilation.keep-alive-seconds` |
| N/A                                        | `Integer` | Queue size for the compilation executor.           | `4`                                  | `compileflow.executor.compilation.queue-size`         |
| N/A                                        | `Long`    | Keep-alive time for execution threads (seconds).   | `180`                                | `compileflow.executor.execution.keep-alive-seconds`   |
| N/A                                        | `Integer` | Queue size for the execution executor.             | `32`                                 | `compileflow.executor.execution.queue-size`           |
| N/A                                        | `Integer` | Number of threads for the event bus.               | `2`                                  | `compileflow.executor.event-threads`                  |
| N/A                                        | `Integer` | Number of threads for the internal scheduler.      | `max(2, CPUs / 8)`                   | `compileflow.executor.schedule-threads`               |

### Cache

| Property (YAML)                                  | Type      | Description                                                                  | Default | System Property Key                              |
|--------------------------------------------------|-----------|------------------------------------------------------------------------------|---------|--------------------------------------------------|
| `compileflow.cache.runtime-max-size`             | `Integer` | Max entries in the primary runtime cache. **(Most important cache setting)** | `2048`  | `compileflow.cache.runtime-max-size`             |
| `compileflow.cache.compile-futures-max-size`     | `Integer` | Max entries in the compilation future cache.                                 | `2048`  | `compileflow.cache.compile-futures-max-size`     |
| `compileflow.cache.dynamic-class-max-size`       | `Integer` | Max entries for dynamically generated Java classes.                          | `512`   | `compileflow.cache.dynamic-class-max-size`       |
| `compileflow.cache.dynamic-class-expire-minutes` | `Integer` | Expiration time for the dynamic class cache (minutes).                       | `30`    | `compileflow.cache.dynamic-class-expire-minutes` |

### Compilation

| Property (YAML)                           | Type      | Description                                    | Default | System Property Key                          |
|-------------------------------------------|-----------|------------------------------------------------|---------|----------------------------------------------|
| `compileflow.compilation.timeout-seconds` | `Integer` | Compilation timeout in seconds.                | `3`     | `compileflow.compilation.timeout-seconds`    |
| `compileflow.compilation.max-attempts`    | `Integer` | Maximum retry attempts on compilation failure. | `1`     | `compileflow.compilation.max-attempts`       |
| N/A                                       | `Long`    | Initial backoff for retries (milliseconds).    | `10`    | `compileflow.compilation.initial-backoff-ms` |
| N/A                                       | `Long`    | Maximum backoff for retries (milliseconds).    | `100`   | `compileflow.compilation.max-backoff-ms`     |

### ClassLoader

| Property (YAML)                                         | Type      | Description                                   | Default | System Property Key                                     |
|---------------------------------------------------------|-----------|-----------------------------------------------|---------|---------------------------------------------------------|
| `compileflow.class-loader.maintenance-interval-minutes` | `Integer` | How often to clean up unused dynamic classes. | `20`    | `compileflow.class-loader.maintenance-interval-minutes` |

### Observability

| Property (YAML)                             | Type      | Description                                                  | Default | System Property Key                         |
|---------------------------------------------|-----------|--------------------------------------------------------------|---------|---------------------------------------------|
| `compileflow.observability.enabled`         | `Boolean` | Master switch for all monitoring features (metrics, events). | `false` | `compileflow.observability.enabled`         |
| `compileflow.observability.metrics-enabled` | `Boolean` | Enable collection of performance and throughput metrics.     | `true`  | `compileflow.observability.metrics-enabled` |
| `compileflow.observability.events-async`    | `Boolean` | Process events asynchronously for better performance.        | `true`  | `compileflow.observability.events-async`    |

---

## 4. Programmatic Configuration (Standalone Use)

If you are not using Spring Boot, you can configure the engine programmatically using the `ProcessEngineConfig` builder.

> ⚠️ **Important:** The `ProcessEngine` is a heavyweight object that manages internal thread pools. It is crucial to
> manage its lifecycle properly.
> - In production, always use a **singleton pattern**. Create one engine instance and reuse it.
> - For short-lived applications or tests, use a `try-with-resources` block to ensure the `close()` method is called,
    which gracefully shuts down the thread pools.

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;

// Example: Configure a TBBPM engine with 4 compilation and 32 execution threads.
ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
    .parallelCompilation(true)
    .executors(ProcessExecutorConfig.of(4, 32)) // More explicit than .threads()
    .build();

// Use try-with-resources to ensure the engine is closed properly.
try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
    // Your business logic here...
    // For example: engine.execute(...)
} catch (Exception e) {
    // Handle exceptions
}
```

---

## 5. Performance Tuning Scenarios

Here are some recommended configurations for different environments.

### Development Profile

*Goal: Fast startup, low resource usage.*

```yaml
compileflow:
  executor:
    compilation-threads: 1
    execution-threads: 4
  cache:
    runtime-max-size: 200 # Smaller cache to save memory
  observability:
    enabled: false # Disable for faster startup
```

### Production (High-Throughput) Profile

*Goal: Maximize processing power for CPU-intensive workflows.*

```yaml
compileflow:
  # Use a higher thread count, e.g., 2x to 4x CPU cores, if flows are I/O bound.
  executor:
    compilation-threads: 4
    execution-threads: 32 # Adjust based on CPU cores and load
  cache:
    runtime-max-size: 10000 # Large cache to avoid re-compilation
  observability:
    enabled: true
    metrics-enabled: true
    events-async: true # Use async events to avoid blocking execution threads
```

---

## 6. Verifying Your Configuration

To see the exact configuration that CompileFlow is using at runtime, you can enable `INFO` level logging for the
auto-configuration class.

In your `application.yml`, add:

```yaml
logging:
  level:
    com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineAutoConfiguration: INFO
```

On application startup, you will see detailed logs showing the effective values for all thread pools, caches, and other
settings. This is the best way to confirm that your configuration has been applied correctly.

