# ProcessEngine Resource Management Guide

> ⚠️ **Critical Warning**: A `ProcessEngine` instance is a heavyweight object that manages multiple internal thread
> pools. Creating instances carelessly is the most common cause of severe resource leaks and application instability in
> CompileFlow.

## 1. The Problem: Why Engine Creation is Expensive

Each time you create a `ProcessEngine` instance, you are creating:

- **Compilation Pool**: For compiling process definitions into Java bytecode.
- **Execution Pool**: For running the business logic of your processes.
- **Event Pool**: For handling asynchronous events.
- **Schedule Pool**: For internal scheduled tasks like timeouts.

Together, a single engine instance can manage **16-64 background threads**. Creating an engine for every request or in a
loop will quickly exhaust your server's thread and memory resources, leading to catastrophic failure.

```java
// ❌ ANTI-PATTERN: DO NOT DO THIS!
public void handleRequest(Request request) {
    // Each call creates 16-64 new threads that will never be cleaned up!
    ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.createTbbpm();
    engine.execute(source, request, Response.class);
}
```

## 2. The Golden Rule: One Engine Per Application

For the vast majority of applications, you only need **one singleton `ProcessEngine` instance** for the entire
application lifecycle. This ensures that thread pools are created only once and are efficiently reused across all
requests.

Here are the recommended ways to achieve this.

---

### Solution 1: The Spring Boot Way (Recommended)

If you use Spring Boot, this is the easiest and safest method. The `compileflow-spring-boot-starter` automatically
configures a singleton `ProcessEngine` for you.

**How it works:** Simply inject the engine into your services. Spring manages its lifecycle, including a graceful
shutdown.

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    
    // ✅ CORRECT: Inject the singleton engine managed by Spring.
    @Autowired
    private ProcessEngine<TbbpmModel> engine;
    
    public void processOrder(OrderRequest request) {
        // Safe and efficient: all requests share the same engine and thread pools.
        engine.execute(ProcessSource.fromCode("bpm.order.process"), request, OrderResponse.class);
    }
}
```

---

### Solution 2: The Manual Singleton Way (for Non-Spring Apps)

If you are not using Spring, you must manage the singleton instance yourself. The best practice is to create a static
holder class and register a JVM shutdown hook to ensure the engine is closed properly.

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

public final class ProcessEngineManager {
    
    // ✅ CORRECT: Create a single, static instance.
    private static final ProcessEngine<TbbpmModel> INSTANCE = 
        ProcessEngineFactory.create(ProcessEngineConfig.tbbpm());
    
    static {
        // ✅ CRITICAL: Register a shutdown hook to close the engine on exit.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                INSTANCE.close();
                System.out.println("ProcessEngine shut down gracefully.");
            } catch (Exception e) {
                System.err.println("Error shutting down ProcessEngine: " + e.getMessage());
            }
        }));
    }
    
    public static ProcessEngine<TbbpmModel> getInstance() {
        return INSTANCE;
    }
    
    private ProcessEngineManager() {}
}

// Usage:
// ProcessEngine<TbbpmModel> engine = ProcessEngineManager.getInstance();
```

---

### Solution 3: The `try-with-resources` Way (for Special Cases)

In rare situations, like isolated integration tests or short-lived batch jobs, you may need a temporary engine. In these
cases, **always** use a `try-with-resources` block to guarantee that `engine.close()` is called.

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;

public class BatchProcessor {
    public void processBatch(List<Item> items) {
        // ✅ CORRECT: The engine is guaranteed to be closed at the end of the block.
        try (ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.createTbbpm()) {
            for (Item item : items) {
                engine.execute(ProcessSource.fromCode("batch.item.process"), item, Result.class);
            }
        } // engine.close() is automatically called here.
    }
}
```

> **Warning**: This pattern is less performant because it involves the overhead of creating and tearing down thread
> pools. It should not be used for request processing in a long-running server application.

---

## 3. Resource Monitoring

Even with correct singleton usage, it's wise to monitor your application's resource consumption. Keep an eye on:

- **Thread Count**: The total number of threads in your application should remain stable under load. A continuously
  increasing thread count is a strong indicator of a resource leak. Look for threads named `cf-compilation-`,
  `cf-execution-`, etc.
- **Heap Memory Usage**: Monitor for steady growth in memory that is never reclaimed, which could also indicate a leak.

For detailed instructions on how to set up monitoring with tools like Prometheus and Grafana, or how to use Spring Boot
Actuator for health checks, please see the **[Monitoring Guide](./monitoring.md)**.
