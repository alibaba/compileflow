# ProcessEngine 资源管理

`ProcessEngine` 是线程安全、需要长期复用的运行时。它管理有界 executor、运行时与编译缓存，以及生成类使用的
`ClassLoader`。应在应用装配入口创建 Engine，在请求之间复用，并在应用关闭时释放。

## 1. 选择正确的作用域

每个不同的 **model type 与不可变配置边界** 使用一个 Engine。多数应用只需要一个 TBBPM Engine；同时执行 TBBPM 与 BPMN
的应用需要每种格式各一个长生命周期 Engine。不同 Engine 不共享可变 cache、executor 或生成类 ClassLoader。

不要按请求、流程、租户或版本创建 Engine。流程 identity 与版本路由是请求数据，不是新建 Engine 的理由。只有确实需要不同 model
type、ClassLoader 作用域、扩展快照或资源上限时，才应创建独立 Engine。

由 builder 或依赖注入容器提供的扩展能力归应用所有；只要它们线程安全，就可以有意共享。
`ProcessEngine.close()` 只关闭 Engine 自有资源。

## 2. Spring Boot 生命周期

Starter 创建 application-scoped `ProcessEngine` bean，并随 ApplicationContext 关闭。直接注入并复用：

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessRef;
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
                ProcessDefinition.classpath("bpm.order.process", "flows/order.process.bpm"),
                request,
                OrderResponse.class,
                ProcessExecutionOptions.defaults());
    }
}
```

CompileFlow Workbench Server 因为同时承载两种 model type，内部使用 `ProcessEngineRegistry`。普通 starter 应用不需要依赖这个平台专用
registry。

## 3. 普通 Java 生命周期

将 Engine 与其他应用基础设施一起创建，并指定唯一 owner 负责关闭。命令行和批处理应用使用顶层
`try` 块即可：

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessRef;

import java.util.Map;

public final class Application {

    public static void main(String[] args) {
        try (ProcessEngine engine = ProcessEngineFactory.createTbbpm()) {
            engine.execute(
                    ProcessDefinition.classpath("batch.item.process", "flows/batch/item.process.bpm"),
                    Map.of("itemId", "item-1"))
                    .orElseThrow();
        }
    }
}
```

长时间运行的非 Spring 服务应把 Engine 保存在 composition root，并由宿主生命周期回调调用
`close()`。不需要 JVM 全局可变 registry，也不需要按请求调用静态 factory。

隔离测试和短生命周期工具可以创建临时 Engine。应使用 `try-with-resources`，确保异常路径也会关闭。

## 4. 容量与关闭

Executor 与 runtime 驻留量默认都有界。根据实测负载调整 `ProcessExecutorConfig`、
`ProcessEngineConfig.maxResidentRuntimes` 和
`ProcessEngineConfig.runtimeLoadTimeout`，不要根据流程数或租户数推导线程数。队列饱和时应显式失败，而不是无界分配任务。

`close()` 会立即拒绝新公共操作，并让在途操作排空。操作排空与 Engine 自有 executor 的有序关闭共享
`shutdown.grace-period`（默认 10 秒），而不是每层重新等待一份预算；关闭路径每 50ms 扫描一次线程固定的非负分片计数，请求完成路径
只更新自己的分片，不加锁、不发送通知，也不争用单个全局计数器。宽限期耗尽后会进入强制清理，清除 Engine 自有 cache，并在
`shutdown.force-period` 内中断仍在运行的 executor 任务。异步
callback 在强制清理开始后重入同一 Engine 时会立即得到 closed 错误。如果 worker 在两段预算后仍忽略中断，Engine 会继续清理其他
资源，并让 `close()` 抛出 `IllegalStateException`，而不是假报关闭成功。Java 不能安全强杀调用方线程或忽略中断的代码；宿主仍应先
停止接收新请求并排空在途调用，再关闭 Engine。

## 5. 观察资源所有权

重点监控：

- 以 `compileflow-engine-<id>-` 开头的线程；
- 编译与执行队列的饱和或拒绝；
- runtime cache 大小与驱逐行为；
- 关闭耗时与关闭失败；
- 版本释放后的堆内存或 ClassLoader 持续增长。

存在 `MeterRegistry` 时，Spring 集成通过 Micrometer 暴露有界 Engine gauge。受支持的指标和容量配置
见[监控指南](monitoring.md)与[配置指南](configuration.md)。
