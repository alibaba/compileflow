# CompileFlow 监控与可观测性指南

本指南讲解了如何接入 CompileFlow 的事件系统，以监控引擎性能、收集指标，并深入了解您的流程执行情况。

## 1. 工作原理：SPI 与事件

CompileFlow 的监控能力构建在一个简单而强大的、基于 Java **服务提供者接口 (SPI)** 的事件驱动机制之上。

1. **`ProcessEventListener` 接口**：该系统的核心。您需要实现此接口来创建一个“监听器”，用以响应由引擎发布的事件。
2. **引擎事件**：`ProcessEngine` 会在关键的生命周期节点发布事件，例如当一个流程开始、完成或失败时。
3. **SPI 注册**：您通过在项目的 `META-INF/extensions` 目录下创建一个特定文件来注册您的自定义监听器。引擎会在启动时发现并加载此文件中列出的所有监听器。

这种解耦的方式允许您在不修改引擎核心代码的情况下，添加自定义的监控、日志和指标收集功能。

---

## 2. 启用可观测性

要激活事件系统，您必须首先在配置中启用它。

**`application.yml` (适用于 Spring Boot)**

```yaml
compileflow:
  observability:
    # 启用所有监控功能（指标、事件、追踪）的主开关。
    # 必须为 true，任何监听器才会被触发。
    enabled: true
    # 对于性能关键的应用，请确保事件被异步处理。
    events-async: true
```

**编程式配置**

```java
// 尚不支持通过编程式配置。请使用系统属性作为替代方案：
// -Dcompileflow.observability.enabled=true
// -Dcompileflow.observability.events-async=true
```

---

## 3. 实现一个基础的事件监听器

这是一个简单的监听器示例，它会在流程成功完成时记录日志。

**第一步：创建监听器类**

```java
package com.example.listeners;

import com.alibaba.compileflow.engine.core.event.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @ExtensionRealization 是可选的，但建议为了清晰起见使用。
// 主要的发现机制是 SPI 文件。
@ExtensionRealization
public class SimpleProcessLogger implements ProcessEventListener<ProcessEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleProcessLogger.class);

    @Override
    public void onEvent(ProcessEvent event) {
        // 使用 instanceof 进行类型安全的事件处理
        if (event instanceof ProcessCoreEvents.ExecutionCompleted) {
            ProcessCoreEvents.ExecutionCompleted completedEvent = (ProcessCoreEvents.ExecutionCompleted) event;
            LOGGER.info("流程 [{}] 在 {}ms 内完成。",
                completedEvent.getProcessCode(), completedEvent.getContext().getDurationMs());
        }
    }
}
```

**第二步：通过 SPI 注册监听器**

在您项目的资源目录下创建以下文件：
`META-INF/extensions/com.alibaba.compileflow.engine.core.event.ProcessEventListener`

将您的监听器类的完全限定名添加到此文件中：

```
com.example.listeners.SimpleProcessLogger
```

现在，当引擎启动时，它将自动发现并注册您的 `SimpleProcessLogger`。

---

## 4. 与 Micrometer 集成以收集指标

一个更强大的用例是为像 Prometheus 和 Grafana 这样的监控系统收集指标。此示例展示了如何与 Micrometer 集成。

**第一步：创建 Micrometer 监听器**

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

    // 在真实应用中，通过您的 DI 框架注入 MeterRegistry。
    // 本示例假设它是被传入的。
    public MicrometerMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onEvent(ProcessEvent event) {
        String processCode = event.getProcessCode();
        ProcessEventContext context = event.getContext();

        if (event instanceof ProcessCoreEvents.ExecutionCompleted) {
            Timer.builder("compileflow.execution.duration")
                .description("流程执行耗时")
                .tag("process.code", processCode)
                .tag("status", "success")
                .register(meterRegistry)
                .record(context.getDurationMs(), TimeUnit.MILLISECONDS);
        } else if (event instanceof ProcessCoreEvents.ExecutionFailed) {
            Timer.builder("compileflow.execution.duration")
                .description("流程执行耗时")
                .tag("process.code", processCode)
                .tag("status", "failure")
                .register(meterRegistry)
                .record(context.getDurationMs(), TimeUnit.MILLISECONDS);
        } else if (event instanceof ProcessCoreEvents.CompilationFailed) {
            Counter.builder("compileflow.compilation.failures")
                .description("流程编译失败次数")
                .tag("process.code", processCode)
                .register(meterRegistry)
                .increment();
        }
    }

    // 通过使用 `instanceof` 实现 onEvent，不再需要 `support` 和 `isAsync`
    // 方法，因为基础接口提供了安全的默认实现。
}
```

**第二步：通过 SPI 注册监听器**

将您的新监听器添加到 SPI 文件中：
`META-INF/extensions/com.alibaba.compileflow.engine.core.event.ProcessEventListener`

```
com.example.listeners.SimpleProcessLogger
com.example.listeners.MicrometerMetricsListener
```

---

## 5. Spring Boot 集成

在 Spring Boot 应用中，您可以将监听器注册为一个 Bean，这样可以注入 Spring 管理的组件（如 `MeterRegistry`）。监听器的发现机制基于
SPI/注解扫描；作为 Bean 注册用于依赖注入，并非用于发现。

**第一步：将监听器创建为 Spring Bean**

修改 `MicrometerMetricsListener` 使之成为一个 Spring 组件。**如果使用此方法，您将不再需要 SPI 文件。**

```java
package com.example.config;

// ... (导入)
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
// 这允许您通过 application.yml 来启用/禁用此监听器
@ConditionalOnProperty(name = "compileflow.custom-metrics.enabled", havingValue = "true")
public class MicrometerMetricsListener implements ProcessEventListener<ProcessEvent> {

    private final MeterRegistry meterRegistry;

    // 使用构造函数注入依赖。
    @Autowired
    public MicrometerMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // onEvent 方法与前一个示例保持一致。
    @Override
    public void onEvent(ProcessEvent event) {
        // ... (上面的实现)
    }
}
```

**第二步：添加配置属性**

现在您可以从 `application.yml` 中控制该监听器：

```yaml
compileflow:
  observability:
    enabled: true # 这必须全局开启
  custom-metrics:
    enabled: true # 这是您为这个特定监听器设置的自定义开关

management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health"
```

关于 support 与 isAsync

- 可按需覆盖 `support(ProcessEventExtensionContext context)` 以按上下文条件启用监听器。默认实现返回 `true`。
- 可按需覆盖 `isAsync()` 控制分发模式。默认值为 `true`（异步）。

---

## 6. 推荐指标与仪表盘

以下是一些核心指标和用于您的 Grafana 仪表盘的 PromQL 查询示例。

### 需要追踪的关键指标

- **执行耗时 (Timer)**: `compileflow_execution_duration_seconds`
    - *原因*: 追踪您流程的性能。
    - *标签*: `process.code`, `status` (success/failure)
- **执行计数 (Counter from Timer)**: `compileflow_execution_duration_seconds_count`
    - *原因*: 衡量您流程的吞吐量。
- **编译失败 (Counter)**: `compileflow_compilation_failures_total`
    - *原因*: 当部署了无效的流程定义时向您发出警报。

### PromQL 查询示例

```promql
# 成功流程的95百分位执行时间，按流程代码分组
histogram_quantile(0.95, sum(rate(compileflow_execution_duration_seconds_bucket{status="success"}[5m])) by (le, process_code))

# 按流程代码分组的吞吐量（每秒执行次数）
sum(rate(compileflow_execution_duration_seconds_count[5m])) by (process_code)

# 过去30分钟内的错误率百分比
(sum(rate(compileflow_execution_duration_seconds_count{status="failure"}[30m])) by (process_code) / sum(rate(compileflow_execution_duration_seconds_count[30m])) by (process_code)) * 100

# 过去30分钟内的编译失败率
sum(rate(compileflow_compilation_failures_total[30m]))
```

---

## 7. 最佳实践

- **保持监听器轻快**: 即使事件可以是异步的，监听器也应保持轻量。对于重操作（例如写入数据库），应将其移交给一个独立的、专用的线程池处理。
- **避免高基数标签**: 不要使用具有无限可能值的标签（如 `orderId` 或 `userId`），因为这会压垮您的指标系统。应使用低基数的标签，如
  `process_code`。
- **处理错误**: 将您的监听器逻辑包装在 `try-catch` 块中，以防止有缺陷的监听器中断其他监听器。
- **结合日志**: 使用指标来发现*什么*慢了，并使用结构化日志（带有 `ProcessEventContext` 中的 `traceId`）来找出*为什么*慢。
