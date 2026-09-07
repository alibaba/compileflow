# CompileFlow BOM

Maven Bill of Materials for aligning CompileFlow dependencies to one version. The BOM contains dependency
management only: it adds no runtime classes, process frontend, Spring Boot integration, Deploy provider, or Durable
store.

Import it once when an application uses more than one CompileFlow artifact:

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

Dependencies managed by the BOM can then omit their versions:

```xml
<dependency>
    <groupId>com.alibaba.compileflow</groupId>
    <artifactId>compileflow-spring-boot-starter-tbbpm</artifactId>
</dependency>
```

Use only the artifacts required by the application; importing the BOM does not place managed dependencies on the
classpath.

```bash
./mvnw validate -pl compileflow-bom
```
