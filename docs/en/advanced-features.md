# CompileFlow Advanced Features

This guide covers advanced topics for users who are already familiar with the basics of CompileFlow.

## 🚀 Engine Warm-up (Pre-compilation)

CompileFlow's "compile-then-execute" model means that a process undergoes compilation the first time it's executed, which can cause a "first hit" latency. To eliminate this latency and ensure consistently high performance for all requests, we strongly recommend warming up all frequently used processes when your application starts.

Warming up is the process of pre-compiling flows and caching them. This can be easily achieved using the `deploy` method of the `ProcessAdminService`.

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class FlowPreheatingService implements ApplicationRunner {

    @Autowired
    private ProcessEngine<TbbpmModel> processEngine;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Automatically warm up all critical processes after the application starts
        // The deploy method compiles the process and caches the result, overwriting if it already exists
        processEngine.admin().deploy(
            ProcessSource.fromCode("bpm.order.process"),
            ProcessSource.fromCode("bpm.payment.process"),
            ProcessSource.fromCode("bpm.user.profile.process")
            // ... add more processes to warm up
        );
    }
}
```

**Benefits:**

- **Eliminates First Hit Latency**: The first call to a process is just as fast as subsequent calls.
- **Early Error Detection**: Compilation errors in process files or scripts are discovered at startup time, not at runtime.
- **Rolling Update Friendly**: In a rolling deployment, new instances are fully prepared before they start receiving traffic.

---

## 🔧 Tooling Service

The `ProcessToolingService`, accessed via `engine.tooling()`, provides utilities for inspecting process models and generating their underlying Java source code for debugging purposes.

```java
// Get tooling service from engine
ProcessToolingService<TbbpmModel> tooling = engine.tooling();

// 1. Load process model for analysis
TbbpmModel model = tooling.loadFlowModel(ProcessSource.fromCode("bpm.order.process"));
System.out.println("Process name: " + model.getName());
System.out.println("Start node: " + model.getStartNode().getId());

// 2. Generate Java source code for debugging
String javaCode = tooling.generateJavaCode(ProcessSource.fromCode("bpm.order.process"));
System.out.println("Generated Java code:\n" + javaCode);
```

---

## 📦 Custom ClassLoader

For scenarios where process definitions or their dependencies (e.g., JARs containing service classes) are loaded from external locations, you can provide a custom `ClassLoader` to the engine.

```java
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

// Create custom ClassLoader
ClassLoader customClassLoader = new URLClassLoader(new URL[]{
    new File("/opt/flows/lib").toURI().toURL()
});

// Use engine with custom ClassLoader
ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
    .classLoader(customClassLoader)
    .build();

try (ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.create(config)) {
    ProcessResult<Map<String, Object>> result = engine.execute(source, context);
}
```

---

## 🚨 Frequently Asked Questions

### Q: How to handle execution errors?

`ProcessResult` provides a safe way to handle failures. Always check `isSuccess()` before attempting to get the data.

```java
ProcessResult<Map<String, Object>> result = engine.execute(source, context);

if (!result.isSuccess()) {
    String errorMessage = result.getErrorMessage();
    String traceId = result.getTraceId();

    // Log the error with a trace ID for correlation
    logger.error("Process execution failed, trace ID: {}, error message: {}", traceId, errorMessage);

    // Optionally, handle different error types
    if (errorMessage.contains("compilation")) {
        // Handle compilation errors, maybe trigger an alert
    } else if (errorMessage.contains("timeout")) {
        // Handle timeout errors
    }
}
```

### Q: How to implement process version management?

CompileFlow is stateless and does not manage versions out-of-the-box. Versioning should be handled at the application level by loading the correct process definition. Here are three common strategies:

```java
// Strategy 1: Use different codes to distinguish versions (simple and clear)
ProcessSource v1 = ProcessSource.fromCode("bpm.order.process.v1");
ProcessSource v2 = ProcessSource.fromCode("bpm.order.process.v2");
engine.execute(v2, ...);

// Strategy 2: Load different content for the same code (useful for dynamic updates)
String contentV2 = loadFlowWithVersionFromDatabase("bpm.order.process", "v2");
ProcessSource source = ProcessSource.fromContent("bpm.order.process", contentV2);
engine.execute(source, ...); // Overwrites the cache entry for "bpm.order.process"

// Strategy 3: Use different file paths (good for file-based storage)
ProcessSource v1File = ProcessSource.fromFile("bpm.order.process", "/flows/v1/order.bpm");
ProcessSource v2File = ProcessSource.fromFile("bpm.order.process", "/flows/v2/order.bpm");
// Note: fromFile uses the code to key the cache, so v2File will overwrite v1File.
// For parallel versions with file-based loading, use distinct codes.
```

### Q: How to integrate external services?

You can call Spring Beans or static Java methods directly from within a process definition using an `autoTask`.

```xml
<!-- Call a Spring Bean -->
<autoTask id="callService" name="Call External Service">
    <action type="spring-bean">
        <actionHandle bean="orderService" method="processOrder">
            <var name="orderId" contextVarName="orderId" inOutType="param"/>
            <var name="result" contextVarName="orderResult" inOutType="return"/>
        </actionHandle>
    </action>
</autoTask>

<!-- Call a static Java class method -->
<autoTask id="callJava" name="Call Java Service">
    <action type="java">
        <actionHandle clazz="com.example.OrderService" method="processOrder">
            <var name="orderId" contextVarName="orderId" inOutType="param"/>
            <var name="result" contextVarName="orderResult" inOutType="return"/>
        </actionHandle>
    </action>
</autoTask>
```

---

## 📚 Other Advanced Topics

- **[🔥 Hot Deployment](hot-deploy.md)**: Learn about strategies for zero-downtime process updates in production.
- **[📊 Monitoring & Observability](monitoring.md)**: Dive deep into integrating with events, metrics, and Prometheus.
- **[⚠️ Resource Management](resource-management.md)**: Understand how to manage the engine lifecycle and avoid resource leaks.
- **[🔧 Extension Guide](extension-guide.md)**: Find out how to develop custom extensions via SPI.
- **[⚙️ Configuration Guide](configuration.md)**: See all available configuration options for performance tuning.
- **[📋 Node Support List](node-support.md)**: Get a detailed list of all supported TBBPM & BPMN nodes.
