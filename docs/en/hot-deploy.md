# Hot Deployment

CompileFlow provides powerful hot deployment capabilities for zero-downtime process updates in production environments.

This allows you to modify business logic, fix bugs, or adjust process flows in real-time without needing to restart your application.

## 1. Manual Hot Reload

The simplest way to update a process is to manually trigger a redeployment using the `ProcessAdminService`. When you call `deploy()` for a process code that already exists in the cache, CompileFlow will reload the process definition, recompile it, and atomically replace the old version in the cache.

```java
// Get the admin service from the engine
ProcessAdminService adminService = engine.admin();

// Basic hot reload by process code. The engine will re-lookup the source.
adminService.deploy(ProcessSource.fromCode("bpm.order.process"));

// Hot reload from new XML content fetched from a config center
String newXmlContent = loadUpdatedFlowFromConfigCenter();
ProcessSource source = ProcessSource.fromContent("bpm.order.process", newXmlContent);
adminService.deploy(source);

// Batch hot reload several processes at once
adminService.deploy(
    ProcessSource.fromCode("bpm.order.process"),
    ProcessSource.fromCode("bpm.payment.process")
);

// Hot reload with a custom ClassLoader
ClassLoader customClassLoader = new URLClassLoader(new URL[]{new URL("file:///opt/lib/")});
adminService.deploy(customClassLoader, ProcessSource.fromCode("bpm.order.process"));
```

## 2. Automatic Hot Deployment

For fully automated updates, CompileFlow provides a change detection mechanism that can monitor external sources and trigger a hot deployment when a change is detected.

### a. File System Monitoring

This is useful for both local development (reloading on save) and production environments where process files are managed on a shared file system.

```java
import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.detector.FileSystemChangeDetector;

// Monitor a production directory for changes
FileSystemChangeDetector detector = new FileSystemChangeDetector("/opt/flows");
FlowHotDeployer hotDeployer = new FlowHotDeployer(engine.admin(), detector);
hotDeployer.start(); // Starts the monitoring in a background thread

// To stop monitoring:
// hotDeployer.stop();
```

### b. Nacos Configuration Center

In a microservices architecture, it's common to manage process files in a configuration center like Nacos. `NacosChangeDetector` listens for configuration changes and triggers hot deployment.

```java
import com.alibaba.compileflow.engine.deploy.FlowHotDeployer;
import com.alibaba.compileflow.engine.deploy.detector.NacosChangeDetector;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

// 1. Configure Nacos client
Properties properties = new Properties();
properties.put("serverAddr", "localhost:8848");
ConfigService configService = NacosFactory.createConfigService(properties);

// 2. Specify which dataIds to monitor
List<String> flowDataIds = Arrays.asList("bpm.order.process", "bpm.payment.process");
NacosChangeDetector nacosDetector = new NacosChangeDetector(
    configService,
    flowDataIds,
    "DEFAULT_GROUP"
);

// 3. Start the hot deployer
FlowHotDeployer hotDeployer = new FlowHotDeployer(engine.admin(), nacosDetector);
hotDeployer.start();
```

## 3. Production Use Cases

### a. Business Rule Updates

A common use case is to externalize business rules (e.g., pricing, discounts, risk levels) into a process file. Business analysts can update these rules through an admin panel, which saves the new BPM file. The file system detector picks up the change and hot-deploys the logic, allowing new orders to use the updated rules instantly without a service restart.

### b. A/B Testing and Canary Releases

Hot deployment can be a tool in a canary release strategy.

1.  **Deploy New Version:** Deploy the new process logic with a distinct code (e.g., `bpm.order.process.v2`).
2.  **Route Traffic:** Use application-level logic (e.g., a feature flag based on `userId`) to route a small percentage of traffic to the new process version.
3.  **Monitor:** Analyze the performance and business metrics of `v2`.
4.  **Full Rollout:** Once validated, update the original process file (`bpm.order.process.v1`) with the `v2` content and hot-deploy it. All traffic will now seamlessly use the new version.

### c. Emergency Bug Fixes & Rollbacks

If a bug is discovered in a live process, you can quickly deploy a fix without a full service rollout. Similarly, if a new deployment causes issues, you can instantly hot-deploy the previous stable version of the process file to roll back the change.

```java
// Emergency rollback to a known stable version
engine.admin().deploy(ProcessSource.fromContent("bpm.order.process", stableVersionXml));
```

## 4. Health Checks & Monitoring

After a hot deployment, it's crucial to verify that the new process is valid.

- **Event Listening:** You can implement a `ProcessEventListener` to listen for `ProcessCoreEvents.CompilationCompleted` or `ProcessCoreEvents.CompilationFailed` to know when a hot reload has finished and whether it was successful. See [Monitoring & Observability](monitoring.md) for details.
- **Active Health Check:** You can create a health check endpoint that executes a test case against the newly deployed process to ensure it runs correctly before it's considered "live."

```java
public boolean isProcessHealthy(String processCode) {
    try {
        // Prepare a mock context for a test case
        Map<String, Object> testContext = createTestContextForProcess(processCode);
        ProcessResult<Map<String, Object>> result = engine.execute(
            ProcessSource.fromCode(processCode),
            testContext
        );
        return result.isSuccess();
    } catch (Exception e) {
        logger.error("Health check for process {} failed", processCode, e);
        return false;
    }
}
```


