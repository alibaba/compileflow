# ProcessEngine 资源管理指南

> ⚠️ **严重警告**: `ProcessEngine` 实例是一个重量级对象，它管理着多个内部线程池。随意创建实例是在 CompileFlow
> 中导致严重资源泄漏和应用不稳定的最常见原因。

## 1. 问题所在：为什么引擎创建成本高昂

每当您创建一个 `ProcessEngine` 实例时，您都在创建：

- **编译池 (Compilation Pool)**: 用于将流程定义编译成 Java 字节码。
- **执行池 (Execution Pool)**: 用于运行您流程中的业务逻辑。
- **事件池 (Event Pool)**: 用于处理异步事件。
- **调度池 (Schedule Pool)**: 用于内部的定时任务，如超时处理。

总而言之，一个引擎实例可以管理 **16-64 个后台线程**。为每个请求或在循环中创建一个引擎会迅速耗尽您服务器的线程和内存资源，从而导致灾难性故障。

```java
// ❌ 反面模式：绝对不要这样做！
public void handleRequest(Request request) {
    // 每次调用都会创建 16-64 个新线程，并且它们永远不会被清理！
    ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.createTbbpm();
    engine.execute(source, request, Response.class);
}
```

## 2. 黄金法则：每个应用一个引擎

对于绝大多数应用程序而言，您在整个应用的生命周期中只需要**一个单例的 `ProcessEngine` 实例**。这能确保线程池只被创建一次，并在所有请求中被高效地复用。

以下是实现这一点的推荐方法。

---

### 解决方案一：Spring Boot 方式 (推荐)

如果您使用 Spring Boot，这是最简单、最安全的方法。`compileflow-spring-boot-starter` 会自动为您配置一个单例的
`ProcessEngine`。

**工作原理:** 只需将引擎注入到您的服务中。Spring 会管理其生命周期，包括优雅停机。

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    
    // ✅ 正确做法：注入由 Spring 管理的单例引擎。
    @Autowired
    private ProcessEngine<TbbpmModel> engine;
    
    public void processOrder(OrderRequest request) {
        // 安全且高效：所有请求共享同一个引擎和线程池。
        engine.execute(ProcessSource.fromCode("bpm.order.process"), request, OrderResponse.class);
    }
}
```

---

### 解决方案二：手动单例方式 (适用于非 Spring 应用)

如果您不使用 Spring，则必须自己管理单例实例。最佳实践是创建一个静态持有者类，并注册一个 JVM 关闭钩子以确保引擎被正确关闭。

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

public final class ProcessEngineManager {
    
    // ✅ 正确做法：创建一个唯一的、静态的实例。
    private static final ProcessEngine<TbbpmModel> INSTANCE = 
        ProcessEngineFactory.create(ProcessEngineConfig.tbbpm());
    
    static {
        // ✅ 关键步骤：注册一个关闭钩子，在程序退出时关闭引擎。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                INSTANCE.close();
                System.out.println("ProcessEngine 已优雅关闭。");
            } catch (Exception e) {
                System.err.println("关闭 ProcessEngine 时出错: " + e.getMessage());
            }
        }));
    }
    
    public static ProcessEngine<TbbpmModel> getInstance() {
        return INSTANCE;
    }
    
    private ProcessEngineManager() {}
}

// 用法:
// ProcessEngine<TbbpmModel> engine = ProcessEngineManager.getInstance();
```

---

### 解决方案三：`try-with-resources` 方式 (适用于特殊情况)

在极少数情况下，例如隔离的集成测试或生命周期短暂的批处理作业，您可能需要一个临时的引擎。在这些情况下，**务必**使用
`try-with-resources` 代码块来保证 `engine.close()` 被调用。

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;

public class BatchProcessor {
    public void processBatch(List<Item> items) {
        // ✅ 正确做法：引擎保证在代码块结束时被关闭。
        try (ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.createTbbpm()) {
            for (Item item : items) {
                engine.execute(ProcessSource.fromCode("batch.item.process"), item, Result.class);
            }
        } // engine.close() 会在这里被自动调用。
    }
}
```

> **警告**：这种模式性能较差，因为它包含了创建和销毁线程池的开销。它不应该用于处理长时间运行的服务器应用中的请求。

---

## 3. 资源监控

即使正确使用了单例模式，监控应用的资源消耗也是明智之举。请关注：

- **线程数**：您应用中的总线程数在负载下应保持稳定。持续增长的线程数是资源泄漏的强烈信号。请留意名为 `cf-compilation-`、
  `cf-execution-` 等的线程。
- **堆内存使用**：监控那些持续增长且从未被回收的内存，这也可能表示存在泄漏。

关于如何使用 Prometheus 和 Grafana 等工具设置监控，或如何使用 Spring Boot Actuator 进行健康检查的详细说明，请参阅 *
*[监控指南](./monitoring.md)**。
