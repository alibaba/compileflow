<div align="center">
  <img src="docs/assets/images/compileflow-logo.png" alt="CompileFlow" width="300" />

# CompileFlow

**A high-performance process engine for Java**

[![CompileFlow Workbench Server CI](https://img.shields.io/github/actions/workflow/status/alibaba/compileflow/workbench-server-ci.yml?branch=master&label=server%20ci&logo=github&logoColor=white)](https://github.com/alibaba/compileflow/actions/workflows/workbench-server-ci.yml)
[![Java Core CI](https://img.shields.io/github/actions/workflow/status/alibaba/compileflow/java-core-ci.yml?branch=master&label=java%20core%20ci&logo=github&logoColor=white)](https://github.com/alibaba/compileflow/actions/workflows/java-core-ci.yml)
[![Workbench CI](https://img.shields.io/github/actions/workflow/status/alibaba/compileflow/workbench-ci.yml?branch=master&label=workbench%20ci&logo=github&logoColor=white)](https://github.com/alibaba/compileflow/actions/workflows/workbench-ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/alibaba/compileflow/badge)](https://scorecard.dev/viewer/?uri=github.com/alibaba/compileflow)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-green?logo=OpenJDK&logoColor=white)](docs/en/compatibility-policy.md)
[![License](https://img.shields.io/badge/license-Apache%202-4D7A97.svg?logo=Apache&logoColor=white)](https://www.apache.org/licenses/LICENSE-2.0.html)

[![GitHub Stars](https://img.shields.io/github/stars/alibaba/compileflow?style=social)](https://github.com/alibaba/compileflow/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/alibaba/compileflow?style=social)](https://github.com/alibaba/compileflow/fork)

[中文 README](README_CN.md)

</div>

CompileFlow is a lightweight, high-performance, embeddable, and extensible process engine for Java. It supports TBBPM
and the documented BPMN 2.0 subset.

The CompileFlow Process engine uses stateless, in-memory execution and supports both compiled and interpreted modes.
CompileFlow has been adopted by core systems across Alibaba business platforms, Taobao, Alibaba Cloud, and
international businesses.

For processes that must retain state across application restarts, CompileFlow Durable provides persisted waits, timers,
and reliable handling of external operations.

Developers can use the visual process editor to design workflows and express complex business logic clearly, helping
business designers and software engineers work together more effectively.

## Key capabilities

- **⚡ High-performance execution** — Compile process definitions into reusable Java runtimes or execute them with the
  interpreter.
- **🧩 TBBPM and BPMN** — Use one engine API and runtime model for TBBPM and the documented BPMN 2.0 subset.
- **✅ Java and Spring Boot integration** — Embed a thread-safe engine directly or through Spring Boot, with declared variables,
  preflight validation, typed results, and stable errors.
- **🚦 Versioned deployment** — Publish immutable Versions, update Aliases with revision checks, and route deterministic
  canary traffic with CompileFlow Deploy.
- **⏱️ Durable execution** — Persist waits, timers, and external-operation state, then resume execution after an
  application restart.
- **🖥️ Visual Workbench** — Model and validate processes in the browser, then publish, monitor, and inspect execution
  through the Workbench Server.

## Choose a product surface

| Need                                                               | Use                                                            |
| ------------------------------------------------------------------ | -------------------------------------------------------------- |
| In-process, low-latency execution                                  | `ProcessEngine` with `compileflow-tbbpm` or `compileflow-bpmn` |
| Persisted waits, timers, external operations, and restart recovery | [CompileFlow Durable](compileflow-durable/README.md)           |
| Browser modeling, release management, and execution inspection     | [CompileFlow Workbench](compileflow-workbench/README.md)       |

These surfaces are independent. Adding Workbench or Durable does not make `ProcessEngine.execute(...)` calls persistent.

## Core API

| Type                | Purpose                                                                                |
| ------------------- | -------------------------------------------------------------------------------------- |
| `ProcessEngine`     | Thread-safe execution engine for installed model frontends and immutable configuration |
| `ProcessRef`        | Reference to an exact published Version or a published Alias                           |
| `ProcessDefinition` | Explicit inline or classpath process content                                           |
| `ProcessResult<T>`  | Typed success or failure with stable error information                                 |

Keep one long-lived `ProcessEngine` for each distinct configuration. A single engine discovers every installed
frontend, while each definition carries its model type. Close the engine with the application lifecycle; do not
create an engine per request.

## Quick start

CompileFlow requires JDK 17 or newer. Generated bytecode targets Java 17. CI runs the complete behavioral suite on Java
17 and focused concurrency and dynamic-code compatibility checks on Java 21 and 25.

Build and install the required modules from source:

```bash
./mvnw install -pl compileflow-spring-boot-starter-tbbpm -am -DskipTests
```

Add the Spring Boot starter:

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter-tbbpm</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Inject the application-scoped engine and execute an explicit definition:

```java
@Service
public class OrderService {

    private final ProcessEngine processEngine;

    public OrderService(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    public OrderResult execute(OrderRequest request) {
        ProcessDefinition definition = ProcessDefinition.classpath(
                ProcessModelType.TBBPM, "order.process",
                "flows/order-process.bpm");

        return processEngine.execute(
                        definition,
                        request,
                        OrderResult.class,
                        ProcessExecutionOptions.defaults())
                .orElseThrow();
    }
}
```

For a complete project, run
[`examples/spring-boot-basic`](examples/spring-boot-basic/README.md). The
[quick-start guide](docs/en/quick-start.md) also covers standalone composition, preflight, warm-up, and shutdown.
For a production-shaped HTTP scenario with gateways, a process call, parallel work, iteration, retries, and controlled
errors, run [`examples/spring-boot-order-fulfillment`](examples/spring-boot-order-fulfillment/README.md).

## Execution model

```mermaid
flowchart LR
    definition["TBBPM or BPMN definition"]
    engine["ProcessEngine"]
    semantic["Process Semantic Plan"]
    compile["COMPILED: generate Java, compile"]
    interpret["INTERPRETED: interpret plan, compile expressions"]
    runtime["Loaded Process runtime"]
    result["ProcessResult"]

    definition --> engine --> semantic
    semantic --> compile --> runtime
    semantic --> interpret --> runtime
    runtime --> result
    runtime --> engine
```

CompileFlow uses explicit source identity, immutable deployment versions, bounded extension points, and typed errors.
The executable node subsets and public compatibility promises are listed in
[Supported Surfaces](docs/en/architecture/supported-surfaces.md).

## Documentation

| Goal                              | English                                                          | 中文                                                     |
| --------------------------------- | ---------------------------------------------------------------- | -------------------------------------------------------- |
| Start using the engine            | [Quick Start](docs/en/quick-start.md)                            | [快速开始](docs/zh/quick-start.md)                       |
| Configure and size an application | [Configuration](docs/en/configuration.md)                        | [配置指南](docs/zh/configuration.md)                     |
| Use persisted execution           | [Durable Process](docs/en/durable-process.md)                    | [Durable Process](docs/zh/durable-process.md)            |
| Understand the architecture       | [Architecture](docs/en/architecture/README.md)                   | [架构文档](docs/zh/architecture/README.md)               |
| Check supported contracts         | [Supported Surfaces](docs/en/architecture/supported-surfaces.md) | [支持面清单](docs/zh/architecture/supported-surfaces.md) |
| Operate a deployment              | [Operations](docs/en/operations-playbook.md)                     | [运维手册](docs/zh/operations-playbook.md)               |
| Contribute                        | [Contributing](CONTRIBUTING.md)                                  | [贡献指南（英文）](CONTRIBUTING.md)                      |

The [documentation center](docs/README.md) is the canonical index for task guides, specifications, architecture, and
module documentation. Use [Supported Surfaces](docs/en/architecture/supported-surfaces.md) for compatibility
decisions.

## Build and test

Run the embedded-engine integration suite:

```bash
./mvnw -B test -pl compileflow-integration-tests -am
```

Repository-specific verification commands are documented in the [testing guide](docs/en/testing.md). Release
requirements are enforced by the repository workflows and the checks listed in [CONTRIBUTING.md](CONTRIBUTING.md).

## Adopters

CompileFlow is used across these Alibaba Group businesses and platforms.
Company and product names and logos are trademarks of their respective owners.

<table>
  <tr>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/alibaba.svg" alt="Alibaba Group" width="64" height="64" /><br /><sub>Alibaba Group</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/taobao.svg" alt="Taobao" width="64" height="64" /><br /><sub>Taobao</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/tmall.svg" alt="Tmall" width="64" height="64" /><br /><sub>Tmall</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/alipay.svg" alt="Alipay" width="64" height="64" /><br /><sub>Alipay</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/cainiao.svg" alt="Cainiao" width="64" height="64" /><br /><sub>Cainiao</sub></td>
  </tr>
  <tr>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/aliyun.svg" alt="Alibaba Cloud" width="64" height="64" /><br /><sub>Alibaba Cloud</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/aliexpress.svg" alt="AliExpress" width="64" height="64" /><br /><sub>AliExpress</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/lazada.svg" alt="Lazada" width="64" height="64" /><br /><sub>Lazada</sub></td>
    <td align="center" width="20%"><img src="docs/assets/images/adopters/fliggy.svg" alt="Fliggy" width="64" height="64" /><br /><sub>Fliggy</sub></td>
    <td align="center" width="20%"><sub>…</sub></td>
  </tr>
</table>

## Community

- [Support policy](SUPPORT.md)
- [Contributing](CONTRIBUTING.md)
- [Maintainers](MAINTAINERS.md)
- [Issue tracker](https://github.com/alibaba/compileflow/issues)
- [Security policy](SECURITY.md)

## License

CompileFlow is available under the [Apache License 2.0](LICENSE).
