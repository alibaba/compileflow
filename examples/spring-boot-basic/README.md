# CompileFlow Spring Boot Basic Example

This minimal Spring Boot application runs a classpath TBBPM process. A QLExpress script task adds two to an input value
without invoking a Spring bean.

## Prerequisites

- JDK 17, 21, or 25
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
