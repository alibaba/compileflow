# CompileFlow API Reference

This document provides a detailed reference for CompileFlow's core public APIs. For core concepts and getting started
examples, please read the [main README](../../README.md) first.

---

## 1. `ProcessEngine`

The core process engine interface.

> ⚠️ **Lifecycle**: `ProcessEngine` instances are heavyweight and manage internal thread pools. They **must be used as
singletons**. See the [Resource Management](resource-management.md) guide for more information.

### 1.1. Executing Processes

#### `execute` (for Stateless Processes)

Used to execute a stateless process from start to finish. This is the most common execution method.

```java
// Method 1: Type-Safe DTOs
<I, O> ProcessResult<O> execute(ProcessSource source, I input, Class<O> outputType);

// Method 2: Flexible Map
ProcessResult<Map<String, Object>> execute(ProcessSource source, Map<String, Object> context);
```

#### `trigger` (for Stateful Processes)

Used to drive a stateful process (a state machine) that has already been started and is in a wait state.

```java
// Method 1: With event and Type-Safe DTOs
<E, O> ProcessResult<O> trigger(ProcessSource source, String tag, String event, E payload, Class<O> outputType);

// Method 2: With event and Map
ProcessResult<Map<String, Object>> trigger(ProcessSource source, String tag, String event, Map<String, Object> context);

// Method 3: Without event and Type-Safe DTOs (event is null)
<E, O> ProcessResult<O> trigger(ProcessSource source, String tag, E payload, Class<O> outputType);

// Method 4: Without event and Map (event is null)
ProcessResult<Map<String, Object>> trigger(ProcessSource source, String tag, Map<String, Object> context);
```

- **`tag`**: The unique identifier of a wait node (e.g., `waitTask`) in the process definition.
- **`event`**: An optional event identifier to distinguish different trigger paths on the same wait node.

### 1.2. Accessing Services

#### `admin()`

Gets the administration service, used for deploying and warming up processes.

```java
ProcessAdminService admin();
```

#### `tooling()`

Gets the tooling service, used for code generation and model inspection.

```java
<T extends FlowModel> ProcessToolingService<T> tooling();
```

---

## 2. `ProcessSource`

Defines the source of a process to be executed. All methods require a unique `code` to identify the process in the
cache.

```java
// Load from the classpath (Recommended)
// The engine will search the classpath for a process file matching the code.
static ProcessSource fromCode(String code);

// Load from a filesystem path
static ProcessSource fromFile(String code, String filePath);
static ProcessSource fromFile(String code, File file);

// Load from a specific path within the classpath
static ProcessSource fromClasspath(String code, String resourcePath);

// Load from a URL
static ProcessSource fromUrl(String code, String url);
static ProcessSource fromUrl(String code, URL url);

// Load directly from an XML string content
static ProcessSource fromContent(String code, String xmlContent);
```

---

## 3. `ProcessResult<T>`

The wrapper object returned by the `execute` and `trigger` methods.

### 3.1. Core Methods

```java
// Check if the execution was successful
boolean isSuccess();

// Get the returned data (only valid if isSuccess() is true)
T getData();

// Get the error message (only valid if isSuccess() is false)
String getErrorMessage();

// Get the unique trace ID for this execution
String getTraceId();
```

### 3.2. Convenience Methods

`ProcessResult` provides a rich set of functional-style methods to simplify result handling.

```java
// Returns the value on success or a default value on failure
T orElse(T defaultValue);

// Returns the value on success or executes a Supplier for a fallback value on failure
T orElseGet(Supplier<T> supplier);

// Returns the value on success or throws an exception on failure
T orElseThrow();
<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

// Perform an action with the data on success
ProcessResult<T> onSuccess(Consumer<T> action);

// Perform an action with the error message on failure
ProcessResult<T> onFailure(Consumer<String> action);

// Transform the data of a successful result
<U> ProcessResult<U> map(Function<T, U> mapper);
```

---

## 4. `ProcessAdminService`

Accessed via `engine.admin()`.

```java
// Deploy one or more processes.
// This triggers the compilation and caching of the process.
// Call this at application startup for engine warm-up.
void deploy(ProcessSource... sources);

// Deploy processes with a specific ClassLoader
void deploy(ClassLoader loader, ProcessSource... sources);
```

---

## 5. `ProcessToolingService`

Accessed via `engine.tooling()`.

```java
// Parse a process source into an in-memory FlowModel object for inspection and analysis
T loadFlowModel(ProcessSource processSource);

// Generate the underlying Java source code from a process source for debugging
String generateJavaCode(ProcessSource processSource);
```

---

## 6. `ProcessEngineFactory`

Used to create `ProcessEngine` instances in non-Spring environments.

```java
// Create a TBBPM engine with default configuration
static ProcessEngine<TbbpmModel> createTbbpm();

// Create a BPMN engine with default configuration
static ProcessEngine<BpmnModel> createBpmn();

// Create an engine from a detailed configuration object
static <T extends FlowModel> ProcessEngine<T> create(ProcessEngineConfig config);
```
