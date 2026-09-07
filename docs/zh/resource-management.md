# ProcessEngine 资源管理

`ProcessEngine` 是线程安全、需要长期复用的运行时。它管理容量受限的执行器、运行时与编译缓存，以及生成类使用的
`ClassLoader`。应在应用装配入口创建引擎，在请求之间复用，并在应用关闭时释放。

## 1. 选择正确的作用域

每组独立的**资源与不可变配置**使用一个引擎。同一引擎支持所有已安装的流程格式，
TBBPM 与 BPMN 定义共享容量受限的缓存、执行器和生成类生命周期。

不要按请求、流程、租户或版本创建引擎。流程身份与版本路由属于请求数据，本身不需要独立引擎。
只有确实需要不同的类加载器作用域、扩展快照或资源上限时，才应创建独立引擎。

由构建器或依赖注入容器提供的扩展能力归应用所有；只要它们线程安全，就可以按需共享。
`ProcessEngine.close()` 只关闭引擎自有资源。

## 2. Spring Boot 生命周期

Starter 创建应用级 `ProcessEngine` Bean，并随 `ApplicationContext` 关闭。直接注入并复用即可：

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessResult;
import org.springframework.stereotype.Service;

@Service
public final class OrderService {

    private final ProcessEngine engine;

    public OrderService(ProcessEngine engine) {
        this.engine = engine;
    }

    public ProcessResult<OrderResponse> process(OrderRequest request) {
        return engine.execute(
                ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.order.process", "flows/order.process.bpm"),
                request,
                OrderResponse.class,
                ProcessExecutionOptions.defaults());
    }
}
```

CompileFlow Workbench Server 复用同一个引擎执行两种流程格式。

## 3. 普通 Java 生命周期

将引擎与其他应用基础设施一起创建，并指定唯一的所有者负责关闭。命令行和批处理应用使用顶层
`try` 块即可：

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;

import java.util.Map;

public final class Application {

    public static void main(String[] args) {
        try (ProcessEngine engine = ProcessEngineFactory.create()) {
            engine.execute(
                    ProcessDefinition.classpath(ProcessModelType.TBBPM, "batch.item.process", "flows/batch/item.process.bpm"),
                    Map.of("itemId", "item-1"))
                    .orElseThrow();
        }
    }
}
```

长时间运行的非 Spring 服务应在应用装配入口持有引擎，并由宿主生命周期回调调用
`close()`。不要使用 JVM 全局可变注册表，也不要在每次请求中调用静态工厂。

隔离测试和短生命周期工具可以创建临时引擎。应使用 `try-with-resources`，确保发生异常时也能关闭。

## 4. 容量与关闭

执行器与常驻运行时数量默认都有限制。应根据实测负载调整 `ProcessExecutorConfig`、
`ProcessEngineConfig.maxResidentRuntimes` 和
`ProcessEngineConfig.runtimeLoadTimeout`，不要根据流程数或租户数推导线程数。队列饱和时应显式失败，而不是无界分配任务。

`close()` 会立即拒绝新的公共操作，并等待进行中的操作结束。操作排空与引擎自有执行器的有序关闭共享
`compileflow.engine.shutdown.timeout`（默认 15 秒），而不是每层重新等待一份预算；Java 配置使用
`ProcessEngineConfig.Builder.shutdownTimeout(...)`。同一预算会在内部预留强制终止时间，不提供独立配置项。
强制清理会清除引擎自有缓存，并中断仍在运行的执行器任务。异步回调在强制清理开始后再次进入同一引擎时，会立即收到已关闭错误。
如果工作线程在超时后仍忽略中断，引擎会继续清理其他资源，并让 `close()` 抛出 `IllegalStateException`，而不是报告关闭成功。
Java 无法安全终止忽略中断的调用方代码；宿主应先停止接收新请求并排空进行中的调用，再关闭引擎。

## 5. 观察资源所有权

重点监控：

- 以 `compileflow-engine-<id>-` 开头的线程；
- 编译与执行队列的饱和或拒绝；
- 运行时缓存大小与驱逐行为；
- 关闭耗时与关闭失败；
- 版本释放后的堆内存或类加载器持续增长。

存在 `MeterRegistry` 时，Spring 集成通过 Micrometer 暴露引擎容量指标。受支持的指标和容量配置
见[监控指南](monitoring.md)与[配置指南](configuration.md)。
