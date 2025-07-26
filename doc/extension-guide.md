# CompileFlow 扩展点开发与注册指南

## 1. 扩展体系简介

CompileFlow 支持高度可插拔的扩展机制，允许用户通过 SPI、注解、Spring Bean 等方式注册扩展点和扩展实现，实现业务无侵入扩展和插件化开发。

---

## 2. 核心概念

- **扩展点（ExtensionPoint）**：定义可扩展的接口或方法，供扩展实现。
- **扩展实现（Extension）**：实现扩展点接口的具体类。
- **插件（Plugin）**：批量注册扩展点和扩展实现的载体，可声明依赖关系。
- **扩展管理器（ExtensionManager/PluginManager）**：负责扩展点、扩展实现、插件的自动发现、注册和管理。

---

## 3. 快速入门

### 3.1 定义扩展点

```java
@ExtensionPoint(
    code = "myService",
    name = "我的服务扩展点",
    description = "用于扩展自定义服务",
    group = "service",
    reducePolicy = ReducePolicy.FIRST
)
public interface MyService extends Extension {
    void doSomething();
}
```

### 3.2 实现扩展

```java
@ExtensionRealization(
    priority = 100,
    scenario = "default"
)
public class MyServiceImpl implements MyService {
    @Override
    public void doSomething() {
        // 实现逻辑
    }
}
```

### 3.3 注册和使用扩展

```java
ExtensionManager manager = ExtensionManager.getInstance();
manager.init();
List<MyService> services = manager.getExtensions("myService", MyService.class);
for (MyService service : services) {
    service.doSomething();
}
```

---

## 4. 插件开发

```java
public class MyPlugin extends Plugin {
    // 可重写 getExtensionPointClasses/getExtensionClasses 注册扩展点和实现
}
```
注册插件：
```java
PluginManager manager = PluginManager.getInstance();
manager.init();
```

---

## 5. 扩展点聚合策略（ReducePolicy）

- **FIRST**：只取第一个实现
- **ALL**：聚合所有实现
- **CUSTOM**：自定义聚合逻辑

---

## 6. 支持的注册方式

- **SPI**：通过 `META-INF/extensions/` 或 `META-INF/services/` 配置自动发现
- **注解**：通过 `@ExtensionPoint`、`@ExtensionRealization` 标记
- **Spring Bean**：自动注册为扩展点/实现

---

## 7. 常见问题

- **扩展实现未生效？**  
  检查是否正确标记注解、注册到 ExtensionManager/PluginManager、优先级设置是否合理。
- **如何禁用某个扩展？**  
  可通过配置或实现 isEnabled 方法动态控制。

---

## 8. 参考

- [ExtensionManager Javadoc](../src/main/java/com/alibaba/compileflow/engine/extension/ExtensionManager.java)
- [PluginManager Javadoc](../src/main/java/com/alibaba/compileflow/engine/extension/PluginManager.java)
- [ExtensionPoint 注解](../src/main/java/com/alibaba/compileflow/engine/extension/annotation/ExtensionPoint.java)
- [ExtensionRealization 注解](../src/main/java/com/alibaba/compileflow/engine/extension/annotation/ExtensionRealization.java) 
