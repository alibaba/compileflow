# CompileFlow Spring Boot Basic Example

This example starts a minimal Spring Boot application and runs a TBBPM process from the classpath. The process adds
two to an input value with a QL script action and does not call any Spring bean.

## Prerequisites

- JDK 17 or later
- A local checkout of this repository

Install the TBBPM starter and its dependencies from the repository root:

```bash
./mvnw install -pl compileflow-spring-boot-starter-tbbpm -am -DskipTests
```

## Run

```bash
./mvnw -f examples/spring-boot-basic/pom.xml spring-boot:run
```

When the process completes, the application logs `Sample process completed: result=42`.

## Verify

```bash
./mvnw -f examples/spring-boot-basic/pom.xml test
```

The test starts the Spring context, executes the same process with an input value of `40`, and verifies the result is
`42`.

## What it demonstrates

- Spring Boot auto-configuration of a `ProcessEngine`
- Classpath process resolution with `ProcessDefinition.classpath(...)`
- Strict preflight validation before execution
- Typed `ProcessResult` handling
