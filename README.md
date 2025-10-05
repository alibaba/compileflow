<div align="center">
  <img src="docs/assets/images/compileflow-logo.png" alt="CompileFlow" width="300" />

# CompileFlow

**🚀 A High-Performance, Compile-Then-Execute Process Engine**

*Transforming business processes into optimized Java code for ultimate performance*

[![Github Workflow Build Status](https://img.shields.io/github/actions/workflow/status/alibaba/compileflow/ci.yaml?branch=master&logo=github&logoColor=white)](https://github.com/alibaba/compileflow/actions/workflows/ci.yaml)
[![Maven Central](https://img.shields.io/maven-central/v/com.alibaba.compileflow/compileflow?color=2d545e&logo=apache-maven&logoColor=white)](https://search.maven.org/artifact/com.alibaba.compileflow/compileflow)
[![Java support](https://img.shields.io/badge/Java-8+-green?logo=OpenJDK&logoColor=white)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/license-Apache%202-4D7A97.svg?logo=Apache&logoColor=white)](https://www.apache.org/licenses/LICENSE-2.0.html)

[![GitHub Stars](https://img.shields.io/github/stars/alibaba/compileflow?style=social)](https://github.com/alibaba/compileflow/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/alibaba/compileflow?style=social)](https://github.com/alibaba/compileflow/fork)

[🇨🇳 中文文档](docs/zh/README.md)

</div>

## ✨ What is CompileFlow?

CompileFlow is a lightweight, high-performance, integrable, and extensible process engine.

Focused on pure in-memory, stateless execution, the CompileFlow Process Engine is one of Taobao's original TBBPM workflow engines. It achieves exceptional efficiency by converting process files into Java code, which is then compiled and executed. It currently powers several core systems at Alibaba, including the Business Mid-end Platform and Trading systems.

CompileFlow allows developers to visually design business logic, bridging the gap between business analysts and engineers and making business expression more intuitive and efficient.

### 🎯 Key Highlights

- **⚡ Ultra-High Performance** - A compile-then-execute architecture delivers native Java performance.
- **🔒 Type-Safe** - Strong typing with compile-time validation reduces runtime errors.
- **🔧 Production-Ready** - Seamless Spring Boot integration, monitoring, and enterprise features.
- **📊 Multi-Standard** - Supports both BPMN 2.0 and the TBBPM specification.
- **🎨 Visual Design** - An IntelliJ IDEA plugin is available for visual process modeling.

---

## 🚀 Quick Start

Get CompileFlow up and running in under 2 minutes.

### A) With Spring Boot (Recommended)

This is the easiest and most recommended way to use CompileFlow in most applications.

#### 1️⃣ Add Dependency

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

#### 2️⃣ Inject and Execute

The `ProcessEngine` is auto-configured as a singleton and can be injected directly.

```java
@Service
public class BusinessService {

    @Autowired
    private ProcessEngine<TbbpmModel> processEngine;

    public MyResponse executeProcess(MyRequest request) {
        ProcessSource processSource = ProcessSource.fromCode("my.business.process");

        ProcessResult<MyResponse> result = processEngine.execute(
            processSource,
            request,
            MyResponse.class
        );

        return result.orElseThrow(() -> new RuntimeException(result.getErrorMessage()));
    }
}
```

### B) Standalone Usage (Non-Spring)

For non-Spring applications, the recommended approach is to implement a manual singleton.

#### 1️⃣ Add Dependencies

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-core</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
<!-- Add compileflow-tbbpm or compileflow-bpmn based on your process specification -->
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-tbbpm</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

#### 2️⃣ Create a Singleton Engine and Execute

```java
// ProcessEngineHolder.java
public final class ProcessEngineHolder {
    private static final ProcessEngine<TbbpmModel> INSTANCE = ProcessEngineFactory.createTbbpm();

    private ProcessEngineHolder() {}

    public static ProcessEngine<TbbpmModel> getInstance() {
        return INSTANCE;
    }

    // Call this from your application's shutdown hook
    public static void shutdown() {
        if (INSTANCE != null) {
            INSTANCE.close();
        }
    }
}

// YourApplication.java
public class YourApplication {
    public static void main(String[] args) {
        // At application startup, you can warm-up processes
        ProcessEngineHolder.getInstance().admin().deploy(ProcessSource.fromCode("..."));

        // In your business logic, get the singleton instance
        ProcessEngine<TbbpmModel> engine = ProcessEngineHolder.getInstance();
        engine.execute(...);

        // Register a shutdown hook to ensure resources are released
        Runtime.getRuntime().addShutdownHook(new Thread(ProcessEngineHolder::shutdown));
    }
}
```
> ⚠️ **Important**: For long-running applications (like web services), you must use a singleton. Creating a new engine per request will cause severe performance problems. See the [Resource Management](docs/en/resource-management.md) guide for more details.

---

## 📚 Detailed Documentation

| Document                                         | Description                               |
|--------------------------------------------------|-------------------------------------------|
| [🚀 Quick Start Guide](docs/en/quick-start.md)           | A complete, runnable example in 5 minutes |
| [⚠️ Resource Management](docs/en/resource-management.md) | **Must Read!** How to use the engine singleton correctly to avoid resource leaks |
| [⚙️ Configuration Guide](docs/en/configuration.md)       | All available YAML and programmatic configuration options |
| [API Reference](docs/en/api-reference.md)                | Detailed reference for all public APIs    |
| [🔥 Hot Deployment](docs/en/hot-deploy.md)               | Zero-downtime process update strategies for production |
| [📊 Monitoring & Observability](docs/en/monitoring.md)   | Integrating with events, metrics, and Prometheus |
| [✨ Advanced Features](docs/en/advanced-features.md)     | Engine warm-up, custom ClassLoaders, and more |
| [🔧 Extension Guide](docs/en/extension-guide.md)         | Develop custom extensions via SPI         |
| [📋 Node Support List](docs/en/node-support.md)          | TBBPM & BPMN 2.0 supported node details   |
| [🛠️ Contributing Guide](CONTRIBUTING.md)        | How to contribute code and documentation to the project |

## 🤝 Community

- 💬 **[GitHub Discussions](https://github.com/alibaba/compileflow/discussions)** - Ask questions and share ideas
- 🐛 **[Issue Tracker](https://github.com/alibaba/compileflow/issues)** - Report bugs and request features
- 📧 **[Security Issues](SECURITY.md)** - Report security vulnerabilities

---

<div align="center">

## 📜 License

CompileFlow is licensed under the [Apache License 2.0](LICENSE)

## 🎆 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=alibaba/compileflow&type=Date)](https://star-history.com/#alibaba/compileflow&Date)

**⭐ If CompileFlow helps you, please give us a star! ⭐**

</div>
