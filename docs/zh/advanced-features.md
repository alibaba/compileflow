# CompileFlow 高级特性

本指南涵盖了已熟悉 CompileFlow 基础知识的深度用户所需的高级主题。

## 🚀 引擎预热 (预编译)

CompileFlow的“编译执行”模式意味着流程在首次执行时会有一个编译过程，这可能会导致“首跳”延迟。为了消除这种延迟并确保所有请求都能获得一致的高性能，我们强烈建议在应用启动时对所有常用流程进行预热。

预热就是预先编译流程并将其缓存起来。这可以通过`ProcessAdminService`的`deploy`方法轻松实现。

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
        // 在应用启动后，自动预热所有关键流程
        // deploy方法会编译流程并缓存结果，如果已存在则会覆盖更新
        processEngine.admin().deploy(
            ProcessSource.fromCode("bpm.order.process"),
            ProcessSource.fromCode("bpm.payment.process"),
            ProcessSource.fromCode("bpm.user.profile.process")
            // ... 添加更多需要预热的流程
        );
    }
}
```

**优点:**

- **消除首跳延迟**: 第一次流程调用和后续调用一样快。
- **提前发现错误**: 在应用启动时就能发现流程文件或脚本中的编译错误，而不是在运行时。
- **滚动更新友好**: 在滚动部署新版本时，新实例在接收流量之前就已经准备好了。

---

## 🔧 工具服务

`ProcessToolingService` (通过 `engine.tooling()` 访问) 提供了用于检查流程模型和生成其底层 Java 源代码以进行调试的实用工具。

```java
// 从引擎获取工具服务
ProcessToolingService<TbbpmModel> tooling = engine.tooling();

// 1. 加载流程模型进行分析
TbbpmModel model = tooling.loadFlowModel(ProcessSource.fromCode("bpm.order.process"));
System.out.println("流程名称: " + model.getName());
System.out.println("开始节点: " + model.getStartNode().getId());

// 2. 生成 Java 源代码用于调试
String javaCode = tooling.generateJavaCode(ProcessSource.fromCode("bpm.order.process"));
System.out.println("生成的 Java 代码:\n" + javaCode);
```

---

## 📦 自定义 ClassLoader

对于从外部位置加载流程定义或其依赖项（例如，包含服务类的 JAR 文件）的场景，您可以为引擎提供一个自定义的 `ClassLoader`。

```java
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

// 创建自定义 ClassLoader
ClassLoader customClassLoader = new URLClassLoader(new URL[]{
    new File("/opt/flows/lib").toURI().toURL()
});

// 使用带自定义 ClassLoader 的引擎
ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
    .classLoader(customClassLoader)
    .build();

try (ProcessEngine<TbbpmModel> engine = ProcessEngineFactory.create(config)) {
    ProcessResult<Map<String, Object>> result = engine.execute(source, context);
}
```

---

## 🚨 常见问题

### Q: 如何处理执行错误？

`ProcessResult` 提供了一种安全处理失败的方式。在尝试获取数据之前，请务必检查 `isSuccess()`。

```java
ProcessResult<Map<String, Object>> result = engine.execute(source, context);

if (!result.isSuccess()) {
    String errorMessage = result.getErrorMessage();
    String traceId = result.getTraceId();

    // 使用跟踪ID记录错误以进行关联
    logger.error("流程执行失败，跟踪ID: {}, 错误信息: {}", traceId, errorMessage);

    // 可选地，处理不同类型的错误
    if (errorMessage.contains("compilation")) {
        // 处理编译错误，可能触发警报
    } else if (errorMessage.contains("timeout")) {
        // 处理超时错误
    }
}
```

### Q: 如何实现流程版本管理？

CompileFlow 是无状态的，不直接管理版本。版本控制应在应用层面通过加载正确的流程定义来处理。以下是三种常见的策略：

```java
// 策略 1：使用不同的代码区分版本 (简单明了)
ProcessSource v1 = ProcessSource.fromCode("bpm.order.process.v1");
ProcessSource v2 = ProcessSource.fromCode("bpm.order.process.v2");
engine.execute(v2, ...);

// 策略 2：为相同的代码加载不同的内容 (适用于动态更新)
String contentV2 = loadFlowWithVersionFromDatabase("bpm.order.process", "v2");
ProcessSource source = ProcessSource.fromContent("bpm.order.process", contentV2);
engine.execute(source, ...); // 会覆盖 "bpm.order.process" 的缓存条目

// 策略 3：使用不同的文件路径 (适用于基于文件的存储)
ProcessSource v1File = ProcessSource.fromFile("bpm.order.process", "/flows/v1/order.bpm");
ProcessSource v2File = ProcessSource.fromFile("bpm.order.process", "/flows/v2/order.bpm");
// 注意: fromFile 使用 code 作为缓存的键, 所以 v2File 会覆盖 v1File。
// 对于基于文件的并行版本，请使用不同的 code。
```

### Q: 如何集成外部服务？

您可以直接在流程定义中使用 `autoTask` 调用 Spring Bean 或静态 Java 方法。

```xml
<!-- 调用 Spring Bean -->
<autoTask id="callService" name="调用外部服务">
    <action type="spring-bean">
        <actionHandle bean="orderService" method="processOrder">
            <var name="orderId" contextVarName="orderId" inOutType="param"/>
            <var name="result" contextVarName="orderResult" inOutType="return"/>
        </actionHandle>
    </action>
</autoTask>

<!-- 调用静态 Java 类方法 -->
<autoTask id="callJava" name="调用Java服务">
    <action type="java">
        <actionHandle clazz="com.example.OrderService" method="processOrder">
            <var name="orderId" contextVarName="orderId" inOutType="param"/>
            <var name="result" contextVarName="orderResult" inOutType="return"/>
        </actionHandle>
    </action>
</autoTask>
```

---

## 📚 其他高级主题

- **[🔥 热部署](hot-deploy.md)**: 了解在生产环境中实现零停机流程更新的策略。
- **[📊 监控 & 可观测性](monitoring.md)**: 深入了解如何与事件、指标和 Prometheus 集成。
- **[⚠️ 资源管理](resource-management.md)**: 了解如何管理引擎生命周期并避免资源泄漏。
- **[🔧 扩展指南](extension-guide.md)**: 了解如何通过 SPI 开发自定义扩展。
- **[⚙️ 配置指南](configuration.md)**: 查看所有可用于性能调优的配置选项。
- **[📋 节点支持列表](node-support.md)**: 获取所有支持的 TBBPM 和 BPMN 节点的详细列表。

