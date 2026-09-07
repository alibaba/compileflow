# CompileFlow BPMN Spring Boot Starter

Spring Boot starter for applications using `ProcessEngine` with BPMN. It combines
`compileflow-spring-boot-starter` and `compileflow-bpmn`. Durable and Deploy require separate dependencies and
configuration.

The following dependency assumes that `compileflow-bom` has been imported. Without the BOM, add the starter version:

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter-bpmn</artifactId>
</dependency>
```

The starter contributes no independent Java API. Use `compileflow-api` contracts and configure the engine through
`compileflow.engine.*`. See the [quick start](../docs/en/quick-start.md),
[configuration reference](../docs/en/configuration.md), and
[BPMN node support](../docs/en/node-support.md).

```bash
./mvnw test -pl compileflow-spring-boot-starter-bpmn -am
```
