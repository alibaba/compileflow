# CompileFlow 节点支持列表

本文档详细说明了 CompileFlow 当前版本实际支持的节点类型。所有列出的节点都经过了代码验证，以确保提供完整的解析器和代码生成器支持。

## TBBPM 支持的节点

### 基础节点

- ✅ `start` - 开始节点
- ✅ `end` - 结束节点
- ✅ `autoTask` - 自动任务节点
- ✅ `scriptTask` - 脚本任务节点

### 网关节点

- ✅ `decision` - 决策节点 (排他网关)
- ✅ `parallel` - 并行网关
- ✅ `inclusive` - 包容网关

### 流程控制

- ✅ `loopProcess` - 循环节点
- ✅ `subBpm` - 子流程
- ✅ `continue` - 继续节点 (用于循环内部)
- ✅ `break` - 中断节点 (用于循环内部)

### 状态节点 (用于有状态流程)

- ✅ `waitTask` - 等待任务
- ✅ `waitEventTask` - 等待事件任务

### 其他

- ✅ `note` - 注释节点

## BPMN 2.0 支持的节点

### 事件

- ✅ `startEvent` - 开始事件
- ✅ `endEvent` - 结束事件

### 任务

- ✅ `serviceTask` - 服务任务
- ✅ `scriptTask` - 脚本任务
- ✅ `receiveTask` - 接收任务

### 网关

- ✅ `exclusiveGateway` - 排他网关
- ✅ `parallelGateway` - 并行网关
- ✅ `inclusiveGateway` - 包容网关

### 结构

- ✅ `subProcess` - 子流程
- ✅ `callActivity` - 调用活动

### 消息

- ✅ `message` - 消息定义

## 不支持的节点

以下节点在 XSD 中有定义或已有解析器，但**缺少代码生成器，无法执行**:

### 不支持的 TBBPM 节点

- ❌ `userTask` - 用户任务 (在 XSD 中有定义但无实现)
- ❌ `timerTask` - 定时器任务
- ❌ `eventTask` - 事件任务
- ❌ `signal` - 信号
- ❌ `transaction` - 事务
- ❌ `timeout` - 超时
- ❌ `noop` - 无操作

### 不支持的 BPMN 节点

- ❌ `userTask` - 用户任务 (有解析器但无生成器)
- ❌ `manualTask` - 手工任务
- ❌ `businessRuleTask` - 业务规则任务
- ❌ `sendTask` - 发送任务
- ❌ `signal` - 信号 (有解析器但无生成器)
- ❌ `intermediateEvent` - 中间事件
- ❌ `boundaryEvent` - 边界事件

##替代方案

### 人工任务实现

要实现人工任务功能，推荐使用以下方法：

```xml
<!-- 使用 waitTask 替代 userTask -->
<waitTask id="approval" name="等待审批" tag="waitForApproval">
    <transition to="afterApproval"/>
</waitTask>
```

```java
// 通过 trigger 完成人工任务
ProcessResult<Map<String, Object>> result = engine.trigger(
    ProcessSource.fromCode("approval.flow"),
    "waitForApproval",  // 标签
    approvalData        // 审批结果
);
```

### 定时任务实现

对于定时功能，您可以通过外部调度系统（如 Spring Scheduler、Quartz 等）调用流程：

```java
@Scheduled(fixedRate = 60000)
public void scheduledTask() {
    engine.execute(ProcessSource.fromCode("scheduled.flow"), context);
}
```

## 验证方法

要验证一个节点是否被支持，请检查以下位置对应的 `*Parser.java` 和 `*Generator.java` 文件：

1. **TBBPM**:
    - 解析器: `compileflow-tbbpm/src/main/java/com/alibaba/compileflow/engine/tbbpm/builder/converter/parser/`
    - 生成器: `compileflow-tbbpm/src/main/java/com/alibaba/compileflow/engine/tbbpm/builder/generator/`
2. **BPMN**:
    - 解析器: `compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/builder/converter/parser/`
    - 生成器: `compileflow-bpmn/src/main/java/com/alibaba/compileflow/engine/bpmn/builder/generator/`

只有同时拥有解析器和生成器的节点才能正常执行。

## 更新说明

本文档是基于当前代码库分析生成的。如果增加了新的节点支持，请及时更新本文档。
