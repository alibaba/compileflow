# CompileFlow Extension Guide

This guide explains how to extend and customize the CompileFlow engine using its powerful, SPI-based extension system.

## Choosing the Right Extension Mechanism

CompileFlow offers three primary ways to extend its functionality. Choosing the right one is key.

| If you want to...                                                   | Use this mechanism...                                                     |
|---------------------------------------------------------------------|---------------------------------------------------------------------------|
| **Observe and react** to engine events (e.g., for logging, metrics) | **[Event Listeners](#1-event-listeners-observing-the-engine)**            |
| **Customize or replace** a specific engine behavior                 | **[Extension Points](#2-extension-points-customizing-behavior)**          |
| **Add a major new component**, like a new process type              | **[Service Providers](#3-service-providers-plugging-in-core-components)** |

---

## 1. Event Listeners: Observing the Engine

**When to Use:** Use an event listener when you want to react to engine lifecycle events without modifying the core
logic. This is ideal for:

- Collecting metrics (e.g., execution duration, failure rates).
- Implementing custom logging or auditing.
- Triggering asynchronous side-effects (e.g., sending notifications).

### How It Works

The engine publishes events for key milestones (e.g., `EXECUTION_COMPLETED`). You create a class that implements
`ProcessEventListener`, and the engine will automatically discover it and send it relevant events.

### Step 1: Implement the `ProcessEventListener` Interface

```java
package com.example.listeners;

import com.alibaba.compileflow.engine.core.event.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtensionRealization // Marks this as a discoverable extension
public class AuditLoggerListener implements ProcessEventListener<ProcessEvent> {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("audit");

    @Override
    public void onEvent(ProcessEvent event) {
        if (event instanceof ProcessCoreEvents.ExecutionCompleted) {
            ProcessCoreEvents.ExecutionCompleted completedEvent = (ProcessCoreEvents.ExecutionCompleted) event;
            AUDIT_LOGGER.info("SUCCESS: Process [{}] finished in {}ms. TraceId: {}",
                completedEvent.getProcessCode(),
                completedEvent.getContext().getDurationMs(),
                completedEvent.getContext().getTraceId());
        }
    }

    @Override
    public boolean support(ProcessEventExtensionContext context) {
        // Performance optimization: only receive the events you need.
        return context.getEvent().getEventType() == ProcessEvent.EventType.EXECUTION_COMPLETED;
    }

    @Override
    public boolean isAsync() {
        // Recommended: process events asynchronously to avoid blocking the engine.
        return true;
    }
}
```

### Step 2: Register via SPI

Create the following file in your project's `resources` directory:
`META-INF/extensions/com.alibaba.compileflow.engine.core.event.ProcessEventListener`

Add the fully qualified name of your listener to this file:

```
com.example.listeners.AuditLoggerListener
```

### Best Practices for Event Listeners

- **Keep them fast**: The `onEvent` method should be non-blocking. For any slow operations, hand off the work to a
  separate, dedicated thread pool.
- **Be resilient**: Wrap your code in `try-catch` blocks to prevent a faulty listener from disrupting other listeners or
  the engine itself.
- **Prefer `instanceof`**: Use `instanceof ProcessCoreEvents.SomeEvent` for type-safe and clear event handling instead
  of `event.getEventType()`.

---

## 2. Extension Points: Customizing Behavior

**When to Use:** Use an extension point when you want to override a specific, pluggable piece of engine logic. This is
ideal for:

- Providing multiple implementations of a strategy (e.g., different pricing rules).
- Allowing downstream users of your application to provide their own logic.

### How It Works

This system uses a pair of annotations (`@ExtensionPoint` and `@ExtensionRealization`) and a central `ExtensionInvoker`
to decouple an interface from its implementations.

### Step 1: Define the Extension Point Interface

The `@ExtensionPoint` annotation defines a unique `code` that acts as the key for this extension.

```java
package com.example.extensions;

import com.alibaba.compileflow.engine.core.extension.ExtensionPoint;

public interface PricePolicy {
    @ExtensionPoint(code = "price.policy.calculate")
    int calculatePrice(int quantity, int basePrice);
}
```

### Step 2: Create One or More Realizations

Implementations are marked with `@ExtensionRealization` and can have a `priority`.

```java
package com.example.extensions.impl;

import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.example.extensions.PricePolicy;

@ExtensionRealization(priority = 100) // Higher priority is preferred
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
        return quantity >= 10 ? (int)(total * 0.9) : total; // 10% discount for 10+ items
    }
}
```

### Step 3: Register Realizations via SPI

Create a file named after the fully qualified interface name in the `META-INF/extensions/` directory:
`META-INF/extensions/com.example.extensions.PricePolicy`

List your implementation classes inside:

```
com.example.extensions.impl.StandardPricePolicy
com.example.extensions.impl.BulkDiscountPolicy
```

### Step 4: Invoke the Extension

Use the `ExtensionInvoker` with the `code` you defined in `@ExtensionPoint`. `invokeFirst` will automatically select the
implementation with the highest priority.

```java
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.example.extensions.PricePolicy;

int quantity = 15;
int basePrice = 100;

// The invoker finds all PricePolicy realizations, selects the one with the
// highest priority (StandardPricePolicy, since 100 > 50), and executes the lambda against it.
int finalPrice = ExtensionInvoker.getInstance().invokeFirst(
    "price.policy.calculate",
    (PricePolicy policy) -> policy.calculatePrice(quantity, basePrice)
);

// The ExtensionInvoker also offers other patterns like invokeAll, invokeChain, etc.
```

---

## 3. Service Providers: Plugging in Core Components

**When to Use:** This is the most advanced mechanism and should be used only when you need to provide a fundamental new
piece of functionality that the engine must discover at startup.

- The primary example is implementing a new `ProcessEngineProvider` for a custom process type (e.g., to support a
  different BPM standard).

### How It Works

This uses the standard Java `ServiceLoader` mechanism. You implement a known framework interface and register your
implementation in a `META-INF/services/` file.

### Example: A Custom `ProcessEngineProvider`

```java
package com.example.engine;

import com.alibaba.compileflow.engine.ProcessEngineProvider;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.core.AbstractProcessEngine;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;

// A custom provider for the TBBPM model type
public class CustomTbbpmEngineProvider implements ProcessEngineProvider {
    @Override
    public FlowModelType support() {
        return FlowModelType.TBBPM;
    }

    @Override
    public AbstractProcessEngine<?> createEngine(ProcessEngineConfig config) {
        // Return your custom subclass of AbstractProcessEngine
        return new MyCustomTbbpmEngine(config);
    }
}
```

### Register via SPI

Create the following file in your project's `resources` directory:
`META-INF/services/com.alibaba.compileflow.engine.ProcessEngineProvider`

Add your provider class to it:

```
com.example.engine.CustomTbbpmEngineProvider
```

At startup, `ProcessEngineFactory` will use the `ServiceLoader` to find your provider when asked to create an engine for
the `TBBPM` model type.
