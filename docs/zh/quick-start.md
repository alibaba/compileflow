# CompileFlow 快速开始

先运行仓库中的 Spring Boot 示例，再将同一个 TBBPM 流程嵌入应用。

## 前置条件

- JDK 17、21 或 25；构建与发布以 Java 17 为基线。
- 使用仓库自带的 Maven Wrapper，无需单独安装 Maven。

在仓库根目录验证工具链：

```bash
java -version
./mvnw -version
```

## 运行示例

先安装 Starter 及其模块依赖，再运行示例：

```bash
./mvnw install -pl compileflow-spring-boot-starter-tbbpm -am -DskipTests
cd examples/spring-boot-basic
../../mvnw -f pom.xml spring-boot:run
```

应用会先执行严格预检，再以 `value=40` 运行 `flows/hello.bpm`，并输出：

```text
Sample process completed: result=42
```

运行示例测试：

```bash
../../mvnw -f pom.xml test -Dtest=SampleApplicationTest
```

完整代码位于 [examples/spring-boot-basic](../../examples/spring-boot-basic/README.md)。

## 添加 Starter

在 Spring Boot 4.1 应用中添加 starter：

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter-tbbpm</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

TBBPM Starter 创建一个线程安全的 `ProcessEngine` Bean，并启用 TBBPM。仅使用 BPMN 的应用应选择
`compileflow-spring-boot-starter-bpmn`。需要同时支持两种格式时，组合使用
`compileflow-spring-boot-starter`、`compileflow-tbbpm` 与 `compileflow-bpmn`，并在每份流程定义上显式声明
`ProcessModelType`。

### 对齐多个 CompileFlow 依赖

应用使用多个 CompileFlow 制品时，只需导入一次 BOM，各项依赖无需重复声明版本：

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

BOM 只管理版本，不会向应用添加依赖。

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

严格预检会校验 XML Schema 和流程图，并确认所选执行模式能够正常准备运行时。

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

`ProcessDefinition.classpath` 适合打包在应用内的稳定定义；`ProcessDefinition.inline` 适合工具和校验场景，
不应在请求链路中携带可变业务逻辑。直接提交的流程定义在匹配缓存前都需要解析；高吞吐生产请求应先发布定义，再使用
`ProcessRef.Version` 或 `ProcessRef.Alias`。

## 预检与预热

在应用启动时校验并准备已知流程：

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

`runtime().warmUp(...)` 只在当前引擎的本地缓存中准备指定定义，不创建流程编码或版本绑定；
它不是分布式发布操作，也不会修改别名路由。

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

将 `ProcessEngines.close()` 接入宿主应用的生命周期，不要为每个请求创建引擎。

## 生产环境使用

直接在进程内执行时，可以将稳定的类路径流程定义随应用一起打包，也可以传入由应用管理的不可变内联内容。

使用 CompileFlow Deploy 时：

- 每次发布使用新的不可变版本，不得让同一版本标识对应不同内容；
- 通过带修订号前置条件的别名发布操作切换流量，单独发布版本不会修改路由；
- 只在选定版本完成本地安装后执行，不会降级到旧制品。

无论使用哪种方式，都应将路由属性与流程变量相互隔离，并在接入生产流量前启用指标和引擎事件监听器。

生产环境指南见[热部署](hot-deploy.md)、[配置](configuration.md)、[监控](monitoring.md)与
[支持范围与兼容性](architecture/supported-surfaces.md)。

## 后续阅读

- [API 参考](api-reference.md)
- [TBBPM 规范](specifications/tbbpm.md)
- [BPMN 节点支持](node-support.md)
- [扩展指南](extension-guide.md)
- [Workbench 部署](../../compileflow-workbench/DEPLOYMENT.md)
