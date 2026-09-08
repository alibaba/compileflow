# CompileFlow Quick Start

Start with the Spring Boot sample, then embed the same TBBPM flow in an application.

## Prerequisites

- JDK 17, 21, or 25. Java 17 is the build and release baseline.
- The repository Maven Wrapper; no separate Maven installation is required.

Verify the toolchain from the repository root:

```bash
java -version
./mvnw -version
```

## Run The Sample

Install the starter and its reactor dependencies, then run the sample:

```bash
./mvnw install -pl compileflow-spring-boot-starter-tbbpm -am -DskipTests
cd examples/spring-boot-basic
../../mvnw -f pom.xml spring-boot:run
```

The application performs strict preflight, executes `flows/hello.bpm` with `value=40`, and logs:

```text
Sample process completed: result=42
```

Run the sample's context test with:

```bash
../../mvnw -f pom.xml test -Dtest=SampleApplicationTest
```

The complete source is in [examples/spring-boot-basic](../../examples/spring-boot-basic/README.md).

## Add The Starter

Add the starter to a Spring Boot 4.1 application:

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter-tbbpm</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

The TBBPM starter creates one thread-safe `ProcessEngine` bean and installs the TBBPM frontend. Use
`compileflow-spring-boot-starter-bpmn` for a BPMN-only application. To support both formats, use the format-neutral
`compileflow-spring-boot-starter` plus `compileflow-tbbpm` and `compileflow-bpmn`; declare `ProcessModelType` explicitly
on each definition.

### Align multiple CompileFlow dependencies

When an application uses multiple CompileFlow artifacts, import the BOM once and omit versions from the individual
dependencies:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.alibaba.compileflow</groupId>
            <artifactId>compileflow-bom</artifactId>
            <version>2.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The BOM only manages versions; it does not add dependencies to the application.

## Define A Flow

Place this definition at `src/main/resources/flows/hello.bpm`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpm code="bpm.sample.hello" name="Hello Sample">
    <var name="value" dataType="java.lang.Integer" inOutType="param"/>
    <var name="result" dataType="java.lang.Integer" inOutType="return"/>
    <start id="1" name="Start" g="100,20,30,30">
        <transition g=":-15,20" to="2"/>
    </start>
    <scriptTask id="2" name="Add Two" g="70,100,88,48">
        <action type="script" language="qlexpress">
            <input source="value" target="value" dataType="java.lang.Integer"/>
            <output target="result" dataType="java.lang.Integer"/>
            <code>value + 2</code>
        </action>
        <transition g=":-15,20" to="3"/>
    </scriptTask>
    <end id="3" name="End" g="100,200,30,30"/>
</bpm>
```

Strict preflight validates the XML schema and process graph, then verifies that the selected runtime can be prepared.

## Execute The Flow

Use constructor injection and handle `ProcessResult` explicitly:

```java
@Service
public final class PricingService {

    private final ProcessEngine processEngine;

    public PricingService(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    public int addTwo(int value) {
        ProcessDefinition definition = ProcessDefinition.classpath(
                ProcessModelType.TBBPM, "bpm.sample.hello",
                "flows/hello.bpm");
        ProcessResult<Map<String, Object>> result = processEngine.execute(
                definition,
                Map.of("value", value));

        Map<String, Object> output = result.orElseThrow();
        return (Integer) output.get("result");
    }
}
```

`ProcessDefinition.classpath` is explicit and stable for packaged definitions. `ProcessDefinition.inline` is useful for
tooling and validation but should not carry mutable business logic on request paths. Direct definition sources resolve
source bytes before exact cache matching; publish the definition and use
`ProcessRef.Version` or `ProcessRef.Alias` for a high-throughput production request path.

## Preflight And Warmup

Validate and prepare known flows during application startup:

```java
ProcessDefinition definition = ProcessDefinition.classpath(
        ProcessModelType.TBBPM, "bpm.sample.hello",
        "flows/hello.bpm");
ProcessPreflightReport report = processEngine.tooling()
        .preflight(definition, ProcessPreflightOptions.strict());
if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
    throw new IllegalStateException(
            "Flow preflight failed: "
                    + report.getItems().stream()
                            .filter(item -> item.getStatus()
                                    != ProcessPreflightReport.ItemStatus.PASS)
                            .map(item -> item.getType() + "/" + item.getStatus()
                                    + ": " + item.getMessage())
                            .toList());
}
processEngine.runtime().warmUp(definition);
```

`runtime().warmUp(...)` prepares exact definition content in this engine's local runtime cache without creating a code
or version binding. It is not a distributed release operation and does not mutate an Alias route.

## Standalone Usage

For a non-Spring application, depend on one format module and own a single engine for the application lifecycle:

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-tbbpm</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

```java
public final class ProcessEngines {
    private static final ProcessEngine TBBPM = ProcessEngineFactory.create();

    private ProcessEngines() {
    }

    public static ProcessEngine tbbpm() {
        return TBBPM;
    }

    public static void close() {
        TBBPM.close();
    }
}
```

Register `ProcessEngines.close()` with the host application's lifecycle. Do not create an engine per request.

## Production Use

For direct in-process execution, package stable Classpath definitions with the application or provide immutable Inline
content under application control.

When using CompileFlow Deploy:

- publish each definition as a new immutable version; never reuse a version identifier for different content;
- change traffic through a revision-checked Alias rollout; publishing alone never changes a route;
- execute a selected version only after it is installed locally; do not fall back to an older artifact.

In every mode, keep routing attributes separate from process variables and enable metrics and engine event listeners
before admitting production traffic.

See [Hot Deployment](hot-deploy.md), [Configuration](configuration.md), [Monitoring](monitoring.md), and
[Supported Surfaces](architecture/supported-surfaces.md) for production guidance.

## Related Guides

- [API Reference](api-reference.md)
- [TBBPM Specification](specifications/tbbpm.md)
- [BPMN Node Support](node-support.md)
- [Extension Guide](extension-guide.md)
- [Workbench Deployment](../../compileflow-workbench/DEPLOYMENT.md)
