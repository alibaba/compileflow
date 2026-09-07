# Process format reference

CompileFlow accepts TBBPM and a documented BPMN 2.0 subset. This page records their syntax correspondence and their shared
runtime boundaries.

## 1. Format identity

| Dimension       | TBBPM                                                                                               | BPMN 2.0                                                                                           |
| --------------- | --------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Full name       | Taobao Business Process Model                                                                       | Business Process Model and Notation 2.0                                                            |
| Standardization | CompileFlow-hosted specification (see [TBBPM Specification](tbbpm.md))                              | OMG formal specification                                                                           |
| Portability     | CompileFlow-native                                                                                  | Standard BPMN vocabulary and model exchange; execution remains subset- and extension-dependent     |
| Designed for    | Automation-focused CompileFlow definitions                                                          | Definitions using the documented BPMN subset                                                       |
| Durability      | `ProcessEngine` runtime does not persist continuation; Durable persists the supported TBBPM profile | `ProcessEngine` runtime does not persist continuation; Durable persists the supported BPMN profile |

## 2. Execution model

Both formats normalize exactly once into `ProcessSemanticPlan`. The `ProcessEngine` then selects a compiled
or interpreted `ProcessRuntime`; the compiled realization emits source-neutral specialized Java, compiles it with the
JDK Java Compiler (`--release 17 -proc:none`), and loads it through an isolated `ClassLoader`.

Both formats use the same runtime realizations. Their differences are syntax, supported elements, model exchange, and
extension ownership. Durable is a separate product
surface: both frontends feed the same `DurableMachineLowerer`, Java program compiler, and machine interpreter.

## 3. Element correspondence

### 3.1 Flow control

| Feature    | TBBPM     | BPMN 2.0       | Notes      |
| ---------- | --------- | -------------- | ---------- |
| Start node | `<start>` | `<startEvent>` | Equivalent |
| End node   | `<end>`   | `<endEvent>`   | Equivalent |

### 3.2 Task nodes

| Feature                       | TBBPM                                           | BPMN 2.0 (CompileFlow-supported subset)                                                     | Notes                                                                                   |
| ----------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Auto task                     | `<autoTask>` with first-class `<action>`        | `<serviceTask>` with namespaced `<cf:action>`                                               | Both formats use the same built-in action implementations                               |
| Script task                   | `<scriptTask>` with an action                   | `<scriptTask scriptFormat="..."><script>...</script>`                                       | Same script registry; format-native XML shapes                                          |
| Named trigger entry           | `<waitTask>`                                    | `<receiveTask>`                                                                             | ProcessEngine trigger starts a new invocation; Durable resumes an exact Wait occurrence |
| Event-qualified trigger entry | `<waitEventTask>`                               | intermediate message catch                                                                  | Durable derives the exact Run, node, and event from the authoritative Wait token        |
| Durable timer                 | `<timerTask>`                                   | intermediate timer catch                                                                    | ProcessEngine runtime has no persistent scheduler and fails closed                      |
| Effect Action                 | `<action execution="effect">` on a Process task | `cf:action execution="effect"` on `serviceTask`, or `cf:execution="effect"` on `scriptTask` | Durable execution semantic, not a second business-node identity                         |
| User task                     | not supported (TBBPM targets automated flows)   | rejected as unsupported                                                                     | CompileFlow does not provide an engine-native human-task lifecycle                      |

### 3.3 Gateways

| Feature             | TBBPM         | BPMN 2.0                            | Notes                                                                     |
| ------------------- | ------------- | ----------------------------------- | ------------------------------------------------------------------------- |
| Exclusive gateway   | `<exclusive>` | `<exclusiveGateway>`                | Equivalent                                                                |
| Parallel gateway    | `<parallel>`  | `<parallelGateway>`                 | Equivalent                                                                |
| Inclusive gateway   | `<inclusive>` | `<inclusiveGateway>`                | Equivalent                                                                |
| Event-based gateway | not supported | not supported by CompileFlow subset | Requires multi-subscription race semantics outside the supported profiles |
| Complex gateway     | not supported | not supported by CompileFlow subset | Out of scope                                                              |

### 3.4 Sub-processes and loops

| Feature                         | TBBPM                    | BPMN 2.0 (CompileFlow-supported subset) | Notes                                                                             |
| ------------------------------- | ------------------------ | --------------------------------------- | --------------------------------------------------------------------------------- |
| Embedded process / process call | `<subBpm>` / `<bpmCall>` | `<subProcess>` / `<callActivity>`       | Both formats distinguish scope from invocation                                    |
| foreach                         | `<foreach>`              | `<multiInstanceLoopCharacteristics>`    | Sequential and deterministic ordered parallel modes                               |
| while loop                      | `<while>`                | `<standardLoopCharacteristics>`         | Equivalent bounded/conditional semantics                                          |
| `break`                         | `<break>`                | not supported by BPMN 2.0               | **TBBPM-unique**                                                                  |
| `continue`                      | `<continue>`             | not supported by BPMN 2.0               | **TBBPM-unique**                                                                  |
| Parallel multi-instance         | `execution="parallel"`   | `isSequential="false"`                  | Durable-only; List input, bounded active iterations, optional ordered aggregation |

### 3.5 Other

| Feature             | TBBPM                                                                                                                    | BPMN 2.0 (CompileFlow-supported subset)                                                                         | Notes                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Annotation          | `<note>`                                                                                                                 | `textAnnotation` is rejected                                                                                    | TBBPM notes are design-time elements; the documented BPMN subset does not preserve text annotations |
| Data object         | not supported                                                                                                            | not supported by CompileFlow subset                                                                             | Use process variables                                                                               |
| Message / signal    | ProcessEngine runtime has no broker semantics; Durable `waitEventTask` resumes one exact token-authorized Run occurrence | `receiveTask` and intermediate message catch lower to exact Durable Wait occurrences; signal nodes are rejected | CompileFlow does not provide general broadcast message/signal correlation                           |
| Deployment identity | Kept outside XML in `ProcessRef` and deploy metadata                                                                     | Kept outside XML in `ProcessRef` and deploy metadata                                                            | Same engine contract                                                                                |

## 4. Actions

TBBPM actions are first-class:

```xml
<autoTask id="task1" g="80,0,120,48">
    <action type="spring-bean" bean="userService"
                      class="com.example.UserService"
                      method="getUser"/>
</autoTask>
```

Every action uses `<action type="..." .../>`. Core invocation types are `java`,
`spring-bean`, and `script`; a Script action's `language` selects QL, Java Code, or another registered language.
See [TBBPM Specification](tbbpm.md) for the full contract.

BPMN 2.0 service tasks use the same action model under standard extension elements:

```xml
<serviceTask id="task1">
    <extensionElements>
        <cf:action type="spring-bean" bean="userService"
                             class="com.example.UserService"
                             method="getUser"/>
    </extensionElements>
</serviceTask>
```

The Java and Spring bean invocation types, the built-in QL and Java Script languages, and explicitly registered custom
Script providers are shared by both formats. The core-provided Java Code executor is trusted in-process and is not a
sandbox.
TBBPM uses direct elements; BPMN keeps CompileFlow extension data namespaced. BPMN `scriptTask` uses its standard
`scriptFormat` and `script` fields rather than wrapping the script in
`cf:action`. See the [BPMN Extension Specification](bpmn-extensions.md)
for the complete `cf:` contract.

## 5. TBBPM profile

TBBPM provides:

- CompileFlow-native process definitions;
- `break` and `continue`, which are not part of the supported BPMN subset;
- first-class action markup without BPMN extension containers;
- the TBBPM examples and editor behavior documented in this repository.

## 6. BPMN profile

The BPMN profile provides:

- standard BPMN XML vocabulary for elements in the CompileFlow-supported subset;
- model exchange through BPMN XML and DI geometry;
- CompileFlow execution attributes under the `cf:` namespace;
- import and validation through the Workbench BPMN designer.

> CompileFlow only executes the BPMN 2.0 subset listed above. Definitions using unsupported nodes such as user tasks,
> transactions, and choreographies fail parsing or `preflight` with a clear error. Parallel multi-instance is supported
> only by Durable execution; optional ordered aggregation uses CompileFlow's explicit output extensions. TBBPM `break`,
> `continue`, and built-in business attributes have no direct mapping to CompileFlow's BPMN subset.

## 7. Format mapping boundaries

### 7.1 TBBPM → BPMN 2.0

Direct mappings exist for: `start`/`end`, `autoTask`→`serviceTask`, `exclusive`→`exclusiveGateway`, `parallel`→
`parallelGateway`, `inclusive`→`inclusiveGateway`, `subBpm`→`subProcess`, `bpmCall`→`callActivity`, `foreach`→
`multiInstanceLoopCharacteristics`.

TBBPM `while` maps to BPMN `standardLoopCharacteristics`. No supported BPMN equivalent exists for `break` or
`continue`; those constructs must be expressed with supported elements when the BPMN subset is required. Deployment
identity does not affect format mapping because both formats keep it outside XML.

### 7.2 BPMN 2.0 → TBBPM

The direct mappings can be inverted. BPMN
`multiInstanceLoopCharacteristics` maps to TBBPM `foreach`; BPMN
`standardLoopCharacteristics` maps to TBBPM `while`. Unsupported BPMN nodes such as user tasks,
transactions, and choreographies cannot be represented by supported TBBPM nodes.

## 8. Performance

Both TBBPM and BPMN run on the same compile-then-execute engine. First execution includes compilation; later executions
can reuse an exact generated runtime. The [benchmark guide](../../../compileflow-benchmarks/README.md) documents the JMH
classes, setup boundaries, and reporting rules.

The benchmarks module measures CompileFlow runtime paths and handwritten Java baselines.

Run benchmarks locally with `./mvnw -pl compileflow-benchmarks verify -Pbenchmarks`.

## 9. Further reading

- [TBBPM Specification](tbbpm.md) — formal TBBPM format reference
- [BPMN Extension Specification](bpmn-extensions.md) — CompileFlow's namespaced BPMN execution contract
- [When to use CompileFlow](../when-to-use.md) — scope and anti-scope
- [Supported Surfaces](../architecture/supported-surfaces.md)
- [Extension Guide](../extension-guide.md)
