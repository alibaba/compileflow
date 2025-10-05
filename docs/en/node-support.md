# CompileFlow Node Support List

This document details the node types actually supported by the current version of CompileFlow. All listed nodes have
been code-verified to ensure complete parser and code generator support.

## TBBPM Supported Nodes

### Basic Nodes

- ✅ `start` - Start node
- ✅ `end` - End node
- ✅ `autoTask` - Auto task node
- ✅ `scriptTask` - Script task node

### Gateway Nodes

- ✅ `decision` - Decision node (exclusive gateway)
- ✅ `parallel` - Parallel gateway
- ✅ `inclusive` - Inclusive gateway

### Process Control

- ✅ `loopProcess` - Loop node
- ✅ `subBpm` - Sub-process
- ✅ `continue` - Continue node (used within loops)
- ✅ `break` - Break node (used within loops)

### State Nodes (for stateful processes)

- ✅ `waitTask` - Wait task
- ✅ `waitEventTask` - Wait event task

### Others

- ✅ `note` - Note node

## BPMN 2.0 Supported Nodes

### Events

- ✅ `startEvent` - Start event
- ✅ `endEvent` - End event

### Tasks

- ✅ `serviceTask` - Service task
- ✅ `scriptTask` - Script task
- ✅ `receiveTask` - Receive task

### Gateways

- ✅ `exclusiveGateway` - Exclusive gateway
- ✅ `parallelGateway` - Parallel gateway
- ✅ `inclusiveGateway` - Inclusive gateway

### Structures

- ✅ `subProcess` - Sub-process
- ✅ `callActivity` - Call activity

### Messages

- ✅ `message` - Message definition

## Unsupported Nodes

The following nodes are defined in XSD or have parsers, but **lack code generators and cannot be executed**:

### Unsupported TBBPM Nodes

- ❌ `userTask` - User task (defined in XSD but no implementation)
- ❌ `timerTask` - Timer task
- ❌ `eventTask` - Event task
- ❌ `signal` - Signal
- ❌ `transaction` - Transaction
- ❌ `timeout` - Timeout
- ❌ `noop` - No operation

### Unsupported BPMN Nodes

- ❌ `userTask` - User task (has parser but no generator)
- ❌ `manualTask` - Manual task
- ❌ `businessRuleTask` - Business rule task
- ❌ `sendTask` - Send task
- ❌ `signal` - Signal (has parser but no generator)
- ❌ `intermediateEvent` - Intermediate event
- ❌ `boundaryEvent` - Boundary event

## Alternative Solutions

### Human Task Implementation

To implement human task functionality, the following approach is recommended:

```xml
<!-- Use waitTask instead of userTask -->
<waitTask id="approval" name="Wait for Approval" tag="waitForApproval">
    <transition to="afterApproval"/>
</waitTask>
```

```java
// Complete human task through trigger
ProcessResult<Map<String, Object>> result = engine.trigger(
    ProcessSource.fromCode("approval.flow"),
    "waitForApproval",  // tag
    approvalData        // approval result
);
```

### Scheduled Task Implementation

For scheduled functionality, you can call processes through external scheduling systems (such as Spring Scheduler,
Quartz, etc.):

```java
@Scheduled(fixedRate = 60000)
public void scheduledTask() {
    engine.execute(ProcessSource.fromCode("scheduled.flow"), context);
}
```

## Verification Method

To verify whether a node is supported, check for corresponding `*Parser.java` and `*Generator.java` files in the
following locations:

1. **TBBPM**:
    - Parser: `compileflow-tbbpm/src/main/java/com/alibaba/compileflow/engine/tbbpm/builder/converter/parser/`
    - Generator: `compileflow-tbbpm/src/main/java/com/alibaba/compileflow/engine/tbbpm/builder/generator/`
2. **BPMN**:
    - Parser: `compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/builder/converter/parser/`
    - Generator: `compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/builder/generator/`

Only nodes with both a parser and a generator can be executed.

## Update Notes

This document is generated based on current codebase analysis. Please update this document promptly if new node support
is added.
