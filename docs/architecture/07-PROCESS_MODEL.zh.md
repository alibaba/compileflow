# 流程模型架构

TBBPM 与 BPMN 定义按照以下职责边界和不变量进入 CompileFlow 编译链路。各 XML 元素与属性以格式规范为准。

## 1. 权威来源与范围

不同问题分别以以下文档为准：

| 问题                        | 参考                                                                                                       |
|-----------------------------|------------------------------------------------------------------------------------------------------------|
| TBBPM 语法与执行语义        | [TBBPM 规范](../specs/tbbpm-specification.zh.md)                                                           |
| TBBPM Schema                | [`TBBPM.xsd`](../../compileflow-tbbpm/src/main/resources/TBBPM.xsd)                                        |
| BPMN 扩展语法与执行语义     | [BPMN 扩展规范](../specs/bpmn-extension-specification.zh.md)                                               |
| BPMN 扩展 Schema            | [`CompileFlowBpmnExtensions.xsd`](../../compileflow-bpmn/src/main/resources/CompileFlowBpmnExtensions.xsd) |
| TBBPM 与 BPMN 支持节点      | [节点支持列表](../zh/node-support.md)                                                                      |
| 两种格式的差异              | [TBBPM 与 BPMN 对比](../specs/tbbpm-vs-bpmn.md)                                                            |
| Parser 与 writer 注册一致性 | [`check_spec_impl_parity.py`](../../scripts/check_spec_impl_parity.py)                                     |

XML Schema 负责拒绝结构错误；parser、模型 validator、结构化计划分析器和 Java 编译器继续校验语义。通过 XSD 校验不等于流程一定可执行。

## 2. 模块职责

`compileflow-core` 负责与格式无关的编译和运行契约：

- 流程源加载与大小限制；
- 安全 XML 解析基础设施；
- 内部控制流模型；
- 结构化图分析；
- Java 源码生成与内存编译；
- action、网关、循环和子流程调用的运行时 executor。

`compileflow-tbbpm` 与 `compileflow-bpmn` 分别负责自身的 XML 模型、parser、validator、支持范围内的 writer、节点 generator 和
engine provider。两个模块中的实现模型不是应用公共 API。

两个格式模块都通过 Java `ServiceLoader` 注册 `ProcessEngineProvider`。应用通过
`ProcessEngineFactory` 或 `compileflow.engine.model-type` 显式选择格式。

## 3. 编译链路

```mermaid
flowchart LR
    A["ProcessDefinition"] --> B["有界流程源加载"]
    B --> C["安全 XML 解析"]
    C --> D["格式模型"]
    D --> E["模型校验"]
    E --> F["StructuredControlFlowAnalyzer"]
    F --> G["格式节点生成器"]
    G --> H["Java 源码"]
    H --> I["javac --release 17"]
    I --> J["隔离的生成类"]
    J --> K["流程运行时缓存"]
```

预检、源码生成、预热和首次执行使用同一条链路。它们只在停止阶段上不同，不会各自解释一套流程语义。

生成类实现内部流程运行时契约，是由 engine 管理的缓存产物。应用不应直接实例化或持久化这些类。

## 4. 格式边界

### TBBPM

TBBPM 是 CompileFlow 的紧凑原生格式。`.bpm` 文档使用显式节点与 transition、CompileFlow action、类型化变量和 action
policy。可接受的结构由 XSD 和 TBBPM 规范共同定义。

TBBPM 模块对 `TbbpmElementWriterRegistry` 注册的元素提供规范化回写。Parser 与 writer 的注册必须
同步，避免支持的模型在回写时静默丢失可执行内容。

### BPMN

BPMN 模块接收文档明确列出的 BPMN 2.0 可执行子集。标准元素保留 BPMN namespace 和语义；CompileFlow 特有的 action 与 policy
数据放在 `cf:` 扩展 namespace 中。

符合通用 BPMN 规范不代表一定能由 CompileFlow 执行。协作、补偿或人工工作流等未支持元素会校验失败，不会被静默忽略；原因是当前共享
Process profile 尚未定义这些语义，而不是底层缺少持久化能力。浏览器设计器可以只开放
Java parser 支持范围的一个更小子集；两个边界都记录在节点支持列表中。

### 跨格式 Invocation Policy

TBBPM 与 BPMN 扩展使用同一套生成 action 执行契约：

- 一个 action 最多声明一个 policy element；
- `maxAttempts` 是 `1..100` 的整数，包含首次调用；
- `backoffMultiplier` 是不小于 `1.0` 的有限 double；
- 每个 interval 都是非负、可精确表示为整数毫秒的 ISO-8601 duration；
- 超时取消采用协作式语义：engine 请求中断，当前一次调用仍被确认在运行时不会启动 retry；
- JVM fatal error（`Error`）不会重试，也不会转换为 `onFailure=continue`。

Timeout 或 retry 可能让外部结果不确定，因此实现必须能容忍重复调用与不确定完成。这套同步 policy 不负责分类 Durable
语义；Action 层的独立契约是 `execution=replayable|effect`，可外部观察的工作应放在受治理的 Durable Effect 边界之后。
精确字段与默认值以 [TBBPM 规范](../specs/tbbpm-specification.zh.md)和
[BPMN 扩展规范](../specs/bpmn-extension-specification.zh.md)为准。

## 5. 结构化控制流

CompileFlow 生成结构化 Java，而不是逐 token 解释任意流程图。因此
`StructuredControlFlowAnalyzer` 必须在节点 generator 输出代码之前完成图校验和 lowering。

网关角色由图形状决定：

- 一个入边、多个出边是 split；
- 多个入边、一个出边是 join；
- 同时具有多个入边和多个出边的混合网关会被拒绝，应建模为 join 后接独立 split；
- 非网关节点不允许直接分支。

TBBPM 不需要额外定义 `fork` 与 `join` 元素名。Join 仍然是图中的显式网关节点，只是角色由入边和出边推导。Parallel 和
inclusive split 必须在同类型网关收敛，或者直接终止流程。这样既保持 XML 模型精炼，也不会把同步点变成隐式约定。

分析器使用 dominator 与 post-dominator 关系确定每个分支区域及其唯一收敛点，并把每个可执行节点归属到一个分支体或一段
continuation。共享 continuation 只在收敛后生成一次，不会复制进每个分支。不可达节点、跨容器边、歧义
join、区域交叠和当前不支持的嵌套并发区域都会在预检阶段失败。

## 6. 生成代码与执行

不可变的 `StructuredControlFlowPlan` 会传给各格式的节点 generator。Exclusive 网关只生成被选中的分支；parallel 和 inclusive
网关把分支协调交给 `GatewayExecutor`。

并发分支使用生成的 branch frame，不会共同写入同一个生成流程对象。分析器在生成前校验变量访问，executor 按既定 merge
与失败规则收敛结果。面向用户的并发行为见
[高级特性](../zh/advanced-features.md)。

根变量方向定义所有权。ProcessEngine 执行与 Durable Start 只接受已声明 `param` 构成的封闭、部分 Map；`return` 与 `inner`
保持 Process-owned，未声明字段会在进入应用代码前失败。普通 trigger entry 有意不同：它从已声明根状态的部分 seed 启动一次
新的下游 invocation。Durable completion 不走这条状态 seed 路径，而是恢复已提交 semantic checkpoint，再应用 typed
occurrence result。

Wait 和事件入口节点会生成 trigger entry point，但本地 engine 的 continuation state 仍由调用方管理。
可选 Durable 产品会持久化流程自身拥有的 semantic checkpoint 与已提交 Wait/Timer/Effect 事实；不会持久化
generated Java、bytecode、应用实现或 Provider compatibility。

## 7. 修改流程模型

新增或修改节点、属性时，以下层次必须同时闭环：

1. 更新规范和对应格式 schema（`TBBPM.xsd` 或 `CompileFlowBpmnExtensions.xsd`）；
2. 更新格式模型与 parser；
3. 对支持回写的格式更新规范化 writer；
4. 更新模型和结构化控制流校验；
5. 按需更新节点 generator 与运行时 helper；
6. 按影响范围补充解析、回写、非法拓扑、生成源码、编译和执行测试；
7. 同步中英文节点支持与流程模型文档；
8. 运行 `python3 scripts/check_spec_impl_parity.py`。

不得加入只有 parser 没有执行语义的元素、静默忽略的属性，或绕过结构化计划归属关系的生成逻辑。

## 8. 相关文档

- [TBBPM 规范](../specs/tbbpm-specification.zh.md)
- [BPMN 扩展规范](../specs/bpmn-extension-specification.zh.md)
- [执行流程](04-EXECUTION_FLOW.zh.md)
- [支持边界](06-SUPPORTED_SURFACES.zh.md)
- [参与贡献](../../CONTRIBUTING.md)
