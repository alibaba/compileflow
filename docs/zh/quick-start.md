# CompileFlow 快速开始

先运行仓库中经过测试的 Spring Boot 示例，再用最小示例把同一个 TBBPM 流程嵌入应用。CompileFlow 2.0 当前仍是未发布快照。

## 前置条件

- JDK 17、21 或 25；构建、发布与默认生产镜像统一以 Java 17 为基线。
- 使用仓库自带的 Maven Wrapper，无需单独安装 Maven。

在仓库根目录验证工具链：

```bash
java -version
./mvnw -version
```

## 运行已验证示例

先安装当前 starter 及其 reactor 依赖，再运行示例：

```bash
./mvnw install -pl compileflow-spring-boot-starter -am -DskipTests
cd examples/spring-boot-basic
../../mvnw -f pom.xml spring-boot:run
```

应用会执行严格 preflight，以 `value=40` 运行 `flows/hello.bpm`，并输出：

```text
Sample process completed: result=42
```

运行示例的 context 测试：

```bash
../../mvnw -f pom.xml test -Dtest=SampleApplicationTest
```

示例位于 [examples/spring-boot-basic](../../examples/spring-boot-basic/README.md)，其测试是本指南的可执行事实源。

## 添加 Starter

在 Spring Boot 4.1 应用中添加 starter：

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Starter 创建一个线程安全的 `ProcessEngine` Bean。默认模型类型是 TBBPM；需要 BPMN 时显式配置
`compileflow.engine.model-type=BPMN`。

## 定义流程

将下面的定义保存为 `src/main/resources/flows/hello.bpm`：

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

严格 preflight 会在执行前校验 XML schema、流程图、生成的 Java 源码和编译结果。

## 执行流程

使用构造器注入，并显式处理 `ProcessResult`：

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

`ProcessDefinition.classpath` 适合打包在应用内的稳定定义。`ProcessDefinition.inline` 适合工具和校验场景，
不应在请求链路中携带可变业务逻辑。直接 definition source 都会在精确缓存匹配前解析源码；高吞吐生产请求应先发布定义，再使用
`ProcessRef.Version` 或 `ProcessRef.Alias`。

## Preflight 与预热

在应用启动或发布准备阶段校验并编译已知流程：

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

`runtime().warmUp(...)` 只把精确定义内容编译到当前引擎的本地运行时缓存，不创建 code 或 version binding；
它不是分布式发布操作，也不会修改Alias 路由。

## 独立模式

非 Spring 应用依赖一个格式模块，并在应用生命周期内持有一个引擎：

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

将 `ProcessEngines.close()` 接入宿主应用的生命周期，不要为每个请求创建引擎。

## 生产边界

- 每次发布使用新的不可变版本；不得让同一版本标识对应不同内容。
- 通过带 revision 前置条件的Alias rollout 切流；单独 publish 永远不会修改 route。
- 部署节点只在本地安装完成后执行被选版本，不会降级到旧 artifact。
- 路由属性与流程变量保持隔离，默认环境 alias 使用 `production`。
- 进入生产前启用指标和引擎事件监听器。

生产路径详见[热部署](hot-deploy.md)、[配置](configuration.md)、[监控](monitoring.md)与
[支持面清单](../architecture/06-SUPPORTED_SURFACES.zh.md)。

## 后续阅读

- [API 参考](api-reference.md)
- [TBBPM 规范](../specs/tbbpm-specification.zh.md)
- [BPMN 节点支持](node-support.md)
- [扩展指南](extension-guide.md)
- [Workbench 部署](../../compileflow-workbench/DEPLOYMENT.md)
