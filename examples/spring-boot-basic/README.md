# CompileFlow Spring Boot Sample

Minimal Spring Boot application that installs the CompileFlow starter from this repository and executes a classpath
TBBPM flow that uses only QL script actions (no Spring beans).

## Prerequisites

- JDK 17+
- From the repository root, install the starter once:

```bash
./mvnw install -pl compileflow-spring-boot-starter -am -DskipTests
```

## Run

```bash
cd examples/spring-boot-basic
../../mvnw -f pom.xml spring-boot:run
```

Expected log line: `Sample process completed: result=42`.

## Verify

The context test runs the same strict preflight and execution path as the application:

```bash
../../mvnw -f pom.xml test
```

## What it demonstrates

- Spring Boot auto-configured `ProcessEngine` singleton
- Explicit `ProcessDefinition.classpath(...)` resolution
- Typed `ProcessResult` handling
- Preflight validation before execution

For hot deployment and Workbench Operate, use the Docker stack in
[compileflow-workbench/DEPLOYMENT.md](../../compileflow-workbench/DEPLOYMENT.md).
