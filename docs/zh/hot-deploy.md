# 热部署

CompileFlow 为生产环境中的零停机流程更新提供了强大的热部署功能。

这使您能够实时修改业务逻辑、修复错误或调整流程，而无需重新启动应用程序。

## 1. 手动热重载

更新流程最简单的方法是使用 `ProcessAdminService` 手动触发重新部署。当您为一个已存在于缓存中的流程代码调用 `deploy()` 时，CompileFlow 将重新加载流程定义，重新编译它，并原子性地替换缓存中的旧版本。

```java
// 从引擎获取管理服务
ProcessAdminService adminService = engine.admin();

// 按流程代码基本热重载。引擎将重新查找源。
adminService.deploy(ProcessSource.fromCode("bpm.order.process"));

// 从配置中心获取的新的 XML 内容进行热重载
String newXmlContent = loadUpdatedFlowFromConfigCenter();
ProcessSource source = ProcessSource.fromContent("bpm.order.process", newXmlContent);
adminService.deploy(source);

// 一次性批量热重载多个流程
adminService.deploy(
    ProcessSource.fromCode("bpm.order.process"),
    ProcessSource.fromCode("bpm.payment.process")
);

// 使用自定义 ClassLoader 进行热重载
ClassLoader customClassLoader = new URLClassLoader(new URL[]{new URL("file:///opt/lib/")});
adminService.deploy(customClassLoader, ProcessSource.fromCode("bpm.order.process"));
```

## 2. 自动热部署

对于全自动更新，CompileFlow 提供了一个变更检测机制，可以监控外部源并在检测到变更时触发热部署。

### a. 文件系统监控

这对于本地开发（保存时重新加载）和流程文件在共享文件系统上管理的生产环境都很有用。

```java
import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.detector.FileSystemChangeDetector;

// 监控生产目录的变更
FileSystemChangeDetector detector = new FileSystemChangeDetector("/opt/flows");
FlowHotDeployer hotDeployer = new FlowHotDeployer(engine.admin(), detector);
hotDeployer.start(); // 在后台线程中开始监控

// 停止监控:
// hotDeployer.stop();
```

### b. Nacos 配置中心

在微服务架构中，通常在像 Nacos 这样的配置中心管理流程文件。`NacosChangeDetector` 会监听配置变更并触发热部署。

```java
import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.detector.NacosChangeDetector;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

// 1. 配置 Nacos 客户端
Properties properties = new Properties();
properties.put("serverAddr", "localhost:8848");
ConfigService configService = NacosFactory.createConfigService(properties);

// 2. 指定要监控的 dataId
List<String> flowDataIds = Arrays.asList("bpm.order.process", "bpm.payment.process");
NacosChangeDetector nacosDetector = new NacosChangeDetector(
    configService,
    flowDataIds,
    "DEFAULT_GROUP"
);

// 3. 启动热部署器
FlowHotDeployer hotDeployer = new FlowHotDeployer(engine.admin(), nacosDetector);
hotDeployer.start();
```

## 3. 生产用例

### a. 业务规则更新

一个常见的用例是将业务规则（例如，定价、折扣、风险等级）外部化到流程文件中。业务分析师可以通过管理面板更新这些规则，面板保存新的 BPM 文件。文件系统检测器会检测到变更并热部署逻辑，使得新订单能够立即使用更新后的规则，而无需服务重启。

### b. A/B 测试和灰度发布

热部署可以作为灰度发布策略中的一个工具。

1.  **部署新版本**：使用一个不同的代码（例如 `bpm.order.process.v2`）部署新的流程逻辑。
2.  **路由流量**：使用应用层逻辑（例如，基于 `userId` 的特性开关）将一小部分流量路由到新的流程版本。
3.  **监控**：分析 `v2` 版本的性能和业务指标。
4.  **全量上线**：验证通过后，用 `v2` 的内容更新原始的流程文件（`bpm.order.process.v1`）并进行热部署。所有流量将无缝切换到新版本。

### c. 紧急 Bug 修复和回滚

如果在生产环境的流程中发现了一个 bug，您可以快速部署一个修复，而无需进行完整的服务发布。同样，如果新的部署导致了问题，您可以立即热部署之前稳定的流程文件版本以回滚变更。

```java
// 紧急回滚到已知的稳定版本
engine.admin().deploy(ProcessSource.fromContent("bpm.order.process", stableVersionXml));
```

## 4. 健康检查与监控

热部署之后，验证新流程是否有效至关重要。

- **事件监听**：您可以实现一个 `ProcessEventListener` 来监听 `ProcessCoreEvents.CompilationCompleted` 或 `ProcessCoreEvents.CompilationFailed` 事件，以了解热重载何时完成以及是否成功。详情请参阅[监控 & 可观测性](monitoring.md)。
- **主动健康检查**：您可以创建一个健康检查端点，对新部署的流程执行一个测试用例，以确保其在被认为是“线上可用”之前能正确运行。

```java
public boolean isProcessHealthy(String processCode) {
    try {
        // 为测试用例准备一个模拟上下文
        Map<String, Object> testContext = createTestContextForProcess(processCode);
        ProcessResult<Map<String, Object>> result = engine.execute(
            ProcessSource.fromCode(processCode),
            testContext
        );
        return result.isSuccess();
    } catch (Exception e) {
        logger.error("流程 {} 的健康检查失败", processCode, e);
        return false;
    }
}
```


