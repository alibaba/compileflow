# CompileFlow 配置指南

欢迎使用CompileFlow综合配置指南。本文档涵盖了从基础设置到高级性能调优的所有内容。

我们的理念很简单：

- **默认简单**: 对于99%的使用场景，您只需要配置几个高级选项。
- **需要时强大**: 对于高级场景，我们提供对引擎每个方面的深度、精细控制。

---

## 1. 配置优先级

CompileFlow使用分层配置模型。当在多个地方设置属性时，优先级最高的配置生效。

优先级顺序如下（从高到低）：

1. **编程式配置**: 通过`ProcessEngineConfig.builder()`直接在代码中应用的设置具有最高优先级。它们总是会覆盖任何其他配置源。
2. **Spring Boot (`application.yml`)**: 在您的`application.yml`或`application.properties`
   文件中定义的属性是配置CompileFlow最常见和推荐的方式。
3. **Java系统属性**: 在JVM启动时传递给JVM的属性（例如，`-Dcompileflow.cache.runtime-max-size=5000`）。
4. **内部默认值**: 引擎的内置默认值。

---

## 2. 主要配置（适用于99%的用户）

对于大多数在Spring Boot上运行的应用程序，这就是您需要的全部。只需在您的`application.yml`中添加以下内容：

```yaml
compileflow:
  # [通用] 流程模型类型。TBBPM针对企业工作流进行了优化，
  # 而BPMN遵循国际标准。
  # 选项：TBBPM, BPMN
  model-type: TBBPM

  # [性能] 两个最关键的性能设置。
  executor:
    # 编译流程定义的线程数。
    # 推荐：1-4。默认：Math.min(2, Math.max(1, CPUs/8))。只有在启动时需要编译大量流程时才增加。
    compilation-threads: 2
    # 执行流程的线程数。
    # 推荐：从CPU核心数开始，根据负载调整。默认：Math.max(4, CPUs)。
    execution-threads: 16

  # [缓存] 最重要的缓存设置。
  cache:
    # 内存中保留的已编译流程的最大数量。
    # 更高的值提高性能但消耗更多内存。
    # 推荐：100-500（开发），2000-10000（生产）。
    runtime-max-size: 2000

  # [可观测性] 监控和指标设置。
  observability:
    # 启用所有监控功能的主开关（指标、事件、跟踪）。
    # 对生产环境至关重要。
    enabled: true
```

---

## 3. 完整属性参考

此表提供了所有可用属性的完整列表、默认值及其对应的Java系统属性键。

### 通用

| 属性 (YAML)                                  | 类型        | 描述                       | 默认      | 系统属性键                                      |
|--------------------------------------------|-----------|--------------------------|---------|--------------------------------------------|
| `compileflow.model-type`                   | `String`  | 流程模型类型：`TBBPM` 或 `BPMN`。 | `TBBPM` | N/A                                        |
| `compileflow.parallel-compilation-enabled` | `Boolean` | 启用流程的并行编译。               | `true`  | `compileflow.compilation.parallel-enabled` |

### 执行器

**注意：** 像 `keep-alive` 时间和队列大小这样的高级属性只能通过 Java 系统属性配置，而不是 YAML。

| 属性 (YAML)                                  | 类型        | 描述              | 默认                                   | 系统属性键                                                 |
|--------------------------------------------|-----------|-----------------|--------------------------------------|-------------------------------------------------------|
| `compileflow.executor.compilation-threads` | `Integer` | 流程编译的线程数。       | `Math.min(2, Math.max(1, CPUs / 8))` | `compileflow.executor.compilation-threads`            |
| `compileflow.executor.execution-threads`   | `Integer` | 流程执行的线程数。       | `Math.max(4, CPUs)`                  | `compileflow.executor.execution-threads`              |
| N/A                                        | `Long`    | 编译线程的保持活动时间（秒）。 | `60`                                 | `compileflow.executor.compilation.keep-alive-seconds` |
| N/A                                        | `Integer` | 编译执行器的队列大小。     | `4`                                  | `compileflow.executor.compilation.queue-size`         |
| N/A                                        | `Long`    | 执行线程的保持活动时间（秒）。 | `180`                                | `compileflow.executor.execution.keep-alive-seconds`   |
| N/A                                        | `Integer` | 执行执行器的队列大小。     | `32`                                 | `compileflow.executor.execution.queue-size`           |
| N/A                                        | `Integer` | 事件总线的线程数。       | `2`                                  | `compileflow.executor.event-threads`                  |
| N/A                                        | `Integer` | 内部调度器的线程数。      | `max(2, CPUs / 8)`                   | `compileflow.executor.schedule-threads`               |

### 缓存

| 属性 (YAML)                                        | 类型        | 描述                           | 默认     | 系统属性键                                            |
|--------------------------------------------------|-----------|------------------------------|--------|--------------------------------------------------|
| `compileflow.cache.runtime-max-size`             | `Integer` | 主运行时缓存中的最大条目数。**（最重要的缓存设置）** | `2048` | `compileflow.cache.runtime-max-size`             |
| `compileflow.cache.compile-futures-max-size`     | `Integer` | 编译未来缓存中的最大条目数。               | `2048` | `compileflow.cache.compile-futures-max-size`     |
| `compileflow.cache.dynamic-class-max-size`       | `Integer` | 动态生成的Java类的最大条目数。            | `512`  | `compileflow.cache.dynamic-class-max-size`       |
| `compileflow.cache.dynamic-class-expire-minutes` | `Integer` | 动态类缓存的过期时间（分钟）。              | `30`   | `compileflow.cache.dynamic-class-expire-minutes` |

### 编译

| 属性 (YAML)                                 | 类型        | 描述             | 默认    | 系统属性键                                        |
|-------------------------------------------|-----------|----------------|-------|----------------------------------------------|
| `compileflow.compilation.timeout-seconds` | `Integer` | 编译超时时间（秒）。     | `3`   | `compileflow.compilation.timeout-seconds`    |
| `compileflow.compilation.max-attempts`    | `Integer` | 编译失败时的最大重试次数。  | `1`   | `compileflow.compilation.max-attempts`       |
| N/A                                       | `Long`    | 重试的初始退避时间（毫秒）。 | `10`  | `compileflow.compilation.initial-backoff-ms` |
| N/A                                       | `Long`    | 重试的最大退避时间（毫秒）。 | `100` | `compileflow.compilation.max-backoff-ms`     |

### 类加载器

| 属性 (YAML)                                               | 类型        | 描述                | 默认   | 系统属性键                                                   |
|---------------------------------------------------------|-----------|-------------------|------|---------------------------------------------------------|
| `compileflow.class-loader.maintenance-interval-minutes` | `Integer` | 清理未使用的动态类的频率（分钟）。 | `20` | `compileflow.class-loader.maintenance-interval-minutes` |

### 可观测性

| 属性 (YAML)                                   | 类型        | 描述                 | 默认      | 系统属性键                                       |
|---------------------------------------------|-----------|--------------------|---------|---------------------------------------------|
| `compileflow.observability.enabled`         | `Boolean` | 所有监控功能的主开关（指标、事件）。 | `false` | `compileflow.observability.enabled`         |
| `compileflow.observability.metrics-enabled` | `Boolean` | 启用性能和吞吐量指标的收集。     | `true`  | `compileflow.observability.metrics-enabled` |
| `compileflow.observability.events-async`    | `Boolean` | 异步处理事件以获得更好的性能。    | `true`  | `compileflow.observability.events-async`    |

---

## 4. 编程式配置（独立使用）

如果您不使用Spring Boot，可以使用`ProcessEngineConfig`构建器以编程方式配置引擎。

> ⚠️ **重要提示:** `ProcessEngine` 是一个管理内部线程池的重量级对象。正确管理其生命周期至关重要。
> - 在生产环境中，请始终使用**单例模式**。创建一个引擎实例并复用它。
> - 对于生命周期较短的应用程序或测试，请使用 `try-with-resources` 代码块以确保调用 `close()` 方法，从而优雅地关闭线程池。

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.config.ProcessExecutorConfig;

// 示例：配置一个具有4个编译线程和32个执行线程的TBBPM引擎。
ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
    .parallelCompilation(true)
    .executors(ProcessExecutorConfig.of(4, 32)) // 比 .threads() 更明确
    .build();

// 使用 try-with-resources 确保引擎被正确关闭。
try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
    // 您的业务逻辑...
    // 例如: engine.execute(...)
} catch (Exception e) {
    // 处理异常
}
```

---

## 5. 性能调优场景

以下是一些针对不同环境的推荐配置。

### 开发环境配置

*目标：快速启动，低资源使用。*

```yaml
compileflow:
  executor:
    compilation-threads: 1
    execution-threads: 4
  cache:
    runtime-max-size: 200 # 较小的缓存以节省内存
  observability:
    enabled: false # 禁用以加快启动
```

### 生产环境（高吞吐量）配置

*目标：为CPU密集型工作流最大化处理能力。*

```yaml
compileflow:
  # 如果流程是I/O绑定的，使用更高的线程数，例如CPU核心数的2-4倍。
  executor:
    compilation-threads: 4
    execution-threads: 32 # 根据CPU核心数和负载调整
  cache:
    runtime-max-size: 10000 # 大缓存以避免重新编译
  observability:
    enabled: true
    metrics-enabled: true
    events-async: true # 使用异步事件避免阻塞执行线程
```

---

## 6. 验证您的配置

要查看CompileFlow在运行时使用的确切配置，您可以启用自动配置类的`INFO`级别日志记录。

在您的`application.yml`中，添加：

```yaml
logging:
  level:
    com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineAutoConfiguration: INFO
```

在应用程序启动时，您将看到显示所有线程池、缓存和其他设置的有效值的详细日志。这是确认您的配置已正确应用的最佳方式。
