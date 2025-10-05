# CompileFlow 扩展指南

本指南解释了如何使用 CompileFlow 强大且基于 SPI 的扩展系统来扩展和自定义引擎。

## 选择正确的扩展机制

CompileFlow 提供了三种主要的方式来扩展其功能。选择正确的方式是关键。

| 如果您想...                   | 使用此机制...                    |
|---------------------------|-----------------------------|
| **观察并响应**引擎事件（例如，用于日志、指标） | **[事件监听器](#1-事件监听器观察引擎)**   |
| **自定义或替换**某个特定的引擎行为       | **[扩展点](#2-扩展点自定义行为)**      |
| **添加一个主要的新组件**，例如一个新的流程类型 | **[服务提供者](#3-服务提供者植入核心组件)** |

---

## 1. 事件监听器：观察引擎

**何时使用：** 当您希望在不修改核心逻辑的情况下对引擎的生命周期事件做出反应时，应使用事件监听器。这非常适合：

- 收集指标（例如，执行耗时、失败率）。
- 实现自定义的日志记录或审计。
- 触发异步的副作用（例如，发送通知）。

### 工作原理

引擎会为关键的里程碑（例如 `EXECUTION_COMPLETED`）发布事件。您需要创建一个实现 `ProcessEventListener`
接口的类，引擎会自动发现它并向其发送相关事件。

### 第一步：实现 `ProcessEventListener` 接口

```java
package com.example.listeners;

import com.alibaba.compileflow.engine.core.event.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtensionRealization // 将此类标记为可被发现的扩展
public class AuditLoggerListener implements ProcessEventListener<ProcessEvent> {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("audit");

    @Override
    public void onEvent(ProcessEvent event) {
        if (event instanceof ProcessCoreEvents.ExecutionCompleted) {
            ProcessCoreEvents.ExecutionCompleted completedEvent = (ProcessCoreEvents.ExecutionCompleted) event;
            AUDIT_LOGGER.info("成功: 流程 [{}] 在 {}ms 内完成。追踪ID: {}",
                completedEvent.getProcessCode(),
                completedEvent.getContext().getDurationMs(),
                completedEvent.getContext().getTraceId());
        }
    }
}
```

### 第二步：通过 SPI 注册

在您项目的 `resources` 目录下创建以下文件：
`META-INF/extensions/com.alibaba.compileflow.engine.core.event.ProcessEventListener`

将您的监听器的完全限定名添加到此文件中：

```
com.example.listeners.AuditLoggerListener
```

### 事件监听器的最佳实践

- **保持快速**：`onEvent` 方法应为非阻塞。对于任何慢速操作，请将其交给一个独立的、专用的线程池处理。
- **保持弹性**：将您的代码包装在 `try-catch` 块中，以防止有缺陷的监听器干扰其他监听器或引擎本身。
- **优先使用 `instanceof`**：使用 `instanceof ProcessCoreEvents.SomeEvent` 进行类型安全和清晰的事件处理，而不是
  `event.getEventType()`。

---

## 2. 扩展点：自定义行为

**何时使用：** 当您想要覆盖特定的、可插拔的引擎逻辑时，应使用扩展点。这非常适合：

- 为一个策略提供多种实现（例如，不同的定价规则）。
- 允许您应用的下游用户提供他们自己的逻辑。

### 工作原理

该系统使用一对注解（`@ExtensionPoint` 和 `@ExtensionRealization`）和一个中心的 `ExtensionInvoker` 来将接口与其实现解耦。

### 第一步：定义扩展点接口

`@ExtensionPoint` 注解定义了一个唯一的 `code`，作为此扩展的密钥。

```java
package com.example.extensions;

import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

public interface PricePolicy {
    @ExtensionPoint(code = "price.policy.calculate")
    int calculatePrice(int quantity, int basePrice);
}
```

### 第二步：创建一个或多个实现

实现类标有 `@ExtensionRealization`，并可以设置 `priority`（优先级）。

```java
package com.example.extensions.impl;

import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.example.extensions.PricePolicy;

@ExtensionRealization(priority = 100) // 数值越大，优先级越高
public class StandardPricePolicy implements PricePolicy {
    @Override
    public int calculatePrice(int quantity, int basePrice) {
        return quantity * basePrice;
    }
}

@ExtensionRealization(priority = 50)
public class BulkDiscountPolicy implements PricePolicy {
    @Override
    public int calculatePrice(int quantity, int basePrice) {
        int total = quantity * basePrice;
        return quantity >= 10 ? (int)(total * 0.9) : total; // 10件及以上九折
    }
}
```

### 第三步：通过 SPI 注册实现

在 `META-INF/extensions/` 目录下创建一个以接口完全限定名命名的文件：
`META-INF/extensions/com.example.extensions.PricePolicy`

在其中列出您的实现类：

```
com.example.extensions.impl.StandardPricePolicy
com.example.extensions.impl.BulkDiscountPolicy
```

### 第四步：调用扩展

使用您在 `@ExtensionPoint` 中定义的 `code` 来调用 `ExtensionInvoker`。`invokeFirst` 会自动选择优先级最高的实现。

```java
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.example.extensions.PricePolicy;

int quantity = 15;
int basePrice = 100;

// 调用器会找到所有 PricePolicy 的实现，选择优先级最高的那个（StandardPricePolicy，因为 100 > 50），
// 然后对其执行 lambda 表达式。
int finalPrice = ExtensionInvoker.getInstance().invokeFirst(
    "price.policy.calculate",
    (PricePolicy policy) -> policy.calculatePrice(quantity, basePrice)
);

// ExtensionInvoker 还提供了其他模式，如 invokeAll, invokeChain 等。
```

---

## 3. 服务提供者：植入核心组件

**何时使用：** 这是最高级的机制，仅当您需要提供一个基础性的新功能，且该功能必须在引擎启动时被发现时才应使用。

- 主要示例是为一个自定义流程类型实现一个新的 `ProcessEngineProvider`（例如，为了支持不同的 BPM 标准）。

### 工作原理

这使用了标准的 Java `ServiceLoader` 机制。您需要实现一个已知的框架接口，并在 `META-INF/services/` 文件中注册您的实现。

### 示例：一个自定义的 `ProcessEngineProvider`

```java
package com.example.engine;

import com.alibaba.compileflow.engine.ProcessEngineProvider;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.core.AbstractProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

// TBBPM 模型类型的一个自定义提供者
public class CustomTbbpmEngineProvider implements ProcessEngineProvider {
    @Override
    public FlowModelType support() {
        return FlowModelType.TBBPM;
    }

    @Override
    public AbstractProcessEngine<?> createEngine(ProcessEngineConfig config) {
        // 返回您的 AbstractProcessEngine 自定义子类
        return new MyCustomTbbpmEngine(config);
    }
}
```

### 通过 SPI 注册

在您项目的 `resources` 目录下创建以下文件：
`META-INF/services/com.alibaba.compileflow.engine.ProcessEngineProvider`

将您的提供者类添加到其中：

```
com.example.engine.CustomTbbpmEngineProvider
```

在启动时，当被要求为 `TBBPM` 模型类型创建引擎时，`ProcessEngineFactory` 将使用 `ServiceLoader` 来找到您的提供者。
