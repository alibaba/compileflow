# CompileFlow API 参考

本文档为 CompileFlow 的核心公共 API 提供了详细参考。关于核心概念和入门示例，请先阅读 [主文档](README.md)。

---

## 1. `ProcessEngine`

核心流程引擎接口。

> ⚠️ **生命周期**: `ProcessEngine` 实例是重量级的，并且管理着内部线程池。它必须以**单例**
> 形式使用。更多信息请参阅 [资源管理](resource-management.md)。

### 1.1. 执行流程

#### `execute` (无状态流程)

用于执行从开始到结束的无状态流程。这是最常用的执行方法。

```java
// 方法 1: 类型安全的 DTO
<I, O> ProcessResult<O> execute(ProcessSource source, I input, Class<O> outputType);

// 方法 2: 灵活的 Map
ProcessResult<Map<String, Object>> execute(ProcessSource source, Map<String, Object> context);
```

#### `trigger` (有状态流程)

用于驱动一个已经启动并处于等待状态的有状态流程（状态机）。

```java
// 方法 1: 带事件和类型安全的 DTO
<E, O> ProcessResult<O> trigger(ProcessSource source, String tag, String event, E payload, Class<O> outputType);

// 方法 2: 带事件和 Map
ProcessResult<Map<String, Object>> trigger(ProcessSource source, String tag, String event, Map<String, Object> context);

// 方法 3: 无事件和类型安全的 DTO（event 参数为 null）
<E, O> ProcessResult<O> trigger(ProcessSource source, String tag, E payload, Class<O> outputType);

// 方法 4: 无事件和 Map（event 参数为 null）
ProcessResult<Map<String, Object>> trigger(ProcessSource source, String tag, Map<String, Object> context);
```

- **`tag`**: 流程定义中等待节点（如 `waitTask`）的唯一标识。
- **`event`**: 可选的事件标识，用于在同一个等待节点上区分不同的触发路径。

### 1.2. 获取服务

#### `admin()`

获取管理服务，用于部署和预热流程。

```java
ProcessAdminService admin();
```

#### `tooling()`

获取工具服务，用于代码生成和模型检查。

```java
<T extends FlowModel> ProcessToolingService<T> tooling();
```

---

## 2. `ProcessSource`

定义要执行的流程来源。所有方法都需要一个唯一的 `code` 来在缓存中标识流程。

```java
// 从 Classpath 加载 (推荐)
// 引擎会在 classpath 中搜索与 code 匹配的流程文件
static ProcessSource fromCode(String code);

// 从文件系统路径加载
static ProcessSource fromFile(String code, String filePath);
static ProcessSource fromFile(String code, File file);

// 从 Classpath 中的特定路径加载
static ProcessSource fromClasspath(String code, String resourcePath);

// 从 URL 加载
static ProcessSource fromUrl(String code, String url);
static ProcessSource fromUrl(String code, URL url);

// 直接从 XML 字符串内容加载
static ProcessSource fromContent(String code, String xmlContent);
```

---

## 3. `ProcessResult<T>`

`execute` 和 `trigger` 方法返回的包装对象。

### 3.1. 核心方法

```java
// 检查执行是否成功
boolean isSuccess();

// 获取返回的数据 (仅在 isSuccess() 为 true 时有效)
T getData();

// 获取错误信息 (仅在 isSuccess() 为 false 时有效)
String getErrorMessage();

// 获取本次执行的唯一跟踪ID
String getTraceId();
```

### 3.2. 便捷方法

`ProcessResult` 提供了丰富的函数式方法来简化结果处理。

```java
// 成功时返回值，失败时返回默认值
T orElse(T defaultValue);

// 成功时返回值，失败时执行一个 Supplier 来提供备用值
T orElseGet(Supplier<T> supplier);

// 成功时返回值，失败时抛出异常
T orElseThrow();
<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

// 在成功时对数据执行一个操作
ProcessResult<T> onSuccess(Consumer<T> action);

// 在失败时对错误信息执行一个操作
ProcessResult<T> onFailure(Consumer<String> action);

// 对成功的结果进行数据转换
<U> ProcessResult<U> map(Function<T, U> mapper);
```

---

## 4. `ProcessAdminService`

通过 `engine.admin()` 获取。

```java
// 部署一个或多个流程。
// 这会触发流程的编译和缓存。
// 在应用启动时调用此方法以进行引擎预热。
void deploy(ProcessSource... sources);

// 使用特定的 ClassLoader 部署流程
void deploy(ClassLoader loader, ProcessSource... sources);
```

---

## 5. `ProcessToolingService`

通过 `engine.tooling()` 获取。

```java
// 将流程源解析为内存中的 FlowModel 对象，用于检查和分析
T loadFlowModel(ProcessSource processSource);

// 从流程源生成其底层的 Java 源代码，用于调试
String generateJavaCode(ProcessSource processSource);
```

---

## 6. `ProcessEngineFactory`

用于在非 Spring 环境中创建 `ProcessEngine` 实例。

```java
// 创建一个使用默认配置的 TBBPM 引擎
static ProcessEngine<TbbpmModel> createTbbpm();

// 创建一个使用默认配置的 BPMN 引擎
static ProcessEngine<BpmnModel> createBpmn();

// 从一个详细的配置对象创建引擎
static <T extends FlowModel> ProcessEngine<T> create(ProcessEngineConfig config);
```
