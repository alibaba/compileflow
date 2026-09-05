# CompileFlow Quick Start

Start with the repository's verified Spring Boot sample, then use the minimum example to embed the same TBBPM flow in
an application. CompileFlow 2.0 is currently an unreleased snapshot.

## Prerequisites

- JDK 17, 21, or 25. Java 17 is the build, release, and default production-image baseline.
- The repository Maven Wrapper; no separate Maven installation is required.

Verify the toolchain from the repository root:

```bash
java -version
./mvnw -version
```

## Run The Verified Sample

Install the current starter and its reactor dependencies, then run the sample:

```bash
./mvnw install -pl compileflow-spring-boot-starter -am -DskipTests
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

The sample is maintained at [examples/spring-boot-basic](../../examples/spring-boot-basic/README.md). Its test is the
executable source of truth for this guide.

## Add The Starter

Add the starter to a Spring Boot 4.1 application:

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

The starter creates one thread-safe `ProcessEngine` bean. TBBPM is the default model type; select BPMN explicitly with
`compileflow.engine.model-type=BPMN` when needed.

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

Strict preflight validates the XML schema, process graph, generated Java source, and compilation before execution.

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
                "bpm.sample.hello",
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

Validate and compile known flows during startup or release preparation:

```java
ProcessDefinition definition = ProcessDefinition.classpath(
        "bpm.sample.hello",
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

`runtime().warmUp(...)` compiles exact definition content into this engine's local runtime cache without creating a code
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
    private static final ProcessEngine TBBPM = ProcessEngineFactory.createTbbpm();

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

## Production Boundary

- Publish each definition as a new immutable version; never reuse a version identifier for different content.
- Change traffic through a revision-checked Alias rollout. Publishing alone never changes a route.
- A deployment node executes only the selected version after local installation; it does not fall back to an older
  artifact.
- Keep routing attributes separate from process variables, and use `production` as the default environment alias.
- Enable metrics and engine event listeners before production rollout.

See [Hot Deployment](hot-deploy.md), [Configuration](configuration.md), [Monitoring](monitoring.md), and
[Supported Surfaces](../architecture/06-SUPPORTED_SURFACES.en.md) for the production path.

## Next Steps

- [API Reference](api-reference.md)
- [TBBPM Specification](../specs/tbbpm-specification.en.md)
- [BPMN Node Support](node-support.md)
- [Extension Guide](extension-guide.md)
- [Workbench Deployment](../../compileflow-workbench/DEPLOYMENT.md)
