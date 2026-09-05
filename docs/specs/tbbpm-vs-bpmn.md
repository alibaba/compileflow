# TBBPM And BPMN 2.0

TBBPM is CompileFlow's native format. BPMN 2.0 is an interoperability entry point for teams that already use BPMN models
or BPMN tooling. Interoperability does not mean that vendor extensions execute unchanged on another engine.

## 1. Format identity

| Dimension       | TBBPM                                                                                   | BPMN 2.0                                                                                       |
| --------------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Full name       | TaoBao Business Process Management                                                      | Business Process Model and Notation 2.0                                                        |
| Standardization | CompileFlow-hosted specification (see [TBBPM Specification](tbbpm-specification.en.md)) | OMG formal specification                                                                       |
| Portability     | CompileFlow-native                                                                      | Standard BPMN vocabulary and model exchange; execution remains subset- and extension-dependent |
| Designed for    | Automation-focused CompileFlow definitions                                              | Standard process notation and broad modeling-tool support                                      |
| Durability      | `ProcessEngine` runtime does not persist continuation; Durable persists the supported TBBPM profile  | `ProcessEngine` runtime does not persist continuation; Durable persists the supported BPMN profile          |

## 2. Execution model

Both formats normalize exactly once into `ProcessSemanticPlan`. The `ProcessEngine` then selects a compiled
or interpreted `ProcessRuntime`; the compiled realization emits source-neutral specialized Java, compiles it with the
JDK Java Compiler (`--release 17 -proc:none`), and loads it through an isolated `ClassLoader`.

There is no "BPMN is interpreted, TBBPM is compiled" divide. Both formats use the same runtime realizations. Their
differences are syntax, supported elements, tooling exchange, and extension ownership. Durable is a separate product
surface: both frontends feed the same `DurableMachineLowerer`, Java program compiler, and machine interpreter.

## 3. Node and feature comparison

### 3.1 Flow control

| Feature    | TBBPM     | BPMN 2.0       | Notes      |
| ---------- | --------- | -------------- | ---------- |
| Start node | `<start>` | `<startEvent>` | Equivalent |
| End node   | `<end>`   | `<endEvent>`   | Equivalent |

### 3.2 Task nodes

| Feature                       | TBBPM                                                   | BPMN 2.0 (CompileFlow-supported subset)                                                     | Notes                                                                              |
| ----------------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Auto task                     | `<autoTask>` with first-class `<action>` | `<serviceTask>` with namespaced `<cf:action>`                             | Both formats use the same built-in action implementations                   |
| Script task                   | `<scriptTask>` with an action                           | `<scriptTask scriptFormat="..."><script>...</script>`                                       | Same script registry; format-native XML shapes                                     |
| Named trigger entry           | `<waitTask>`                                            | `<receiveTask>`                                                                             | ProcessEngine trigger starts a new invocation; Durable resumes an exact Wait occurrence |
| Event-qualified trigger entry | `<waitEventTask>`                                       | intermediate message catch                                                                  | Durable requires the Run, one-time token, node ID, and event to match              |
| Durable timer                 | `<timerTask>`                                           | intermediate timer catch                                                                    | ProcessEngine runtime has no persistent scheduler and fails closed                      |
| Effect Action                 | `<action execution="effect">` on a Process task        | `cf:action execution="effect"` on `serviceTask`, or `cf:execution="effect"` on `scriptTask` | Durable execution semantic, not a second business-node identity                    |
| User task                     | not supported (TBBPM targets automated flows)           | rejected as unsupported                                                                     | Long-running human-task workflows belong to Activiti/Camunda/Flowable              |

### 3.3 Gateways

| Feature             | TBBPM         | BPMN 2.0                            | Notes                                                                   |
| ------------------- | ------------- | ----------------------------------- | ----------------------------------------------------------------------- |
| Exclusive gateway   | `<exclusive>`  | `<exclusiveGateway>`                | Equivalent                                                              |
| Parallel gateway    | `<parallel>`  | `<parallelGateway>`                 | Equivalent                                                              |
| Inclusive gateway   | `<inclusive>` | `<inclusiveGateway>`                | Equivalent                                                              |
| Event-based gateway | not supported | not supported by CompileFlow subset | Requires multi-subscription race semantics outside the current profiles |
| Complex gateway     | not supported | not supported by CompileFlow subset | Out of scope                                                            |

### 3.4 Sub-processes and loops

| Feature                         | TBBPM                    | BPMN 2.0 (CompileFlow-supported subset) | Notes                                                                 |
| ------------------------------- | ------------------------ | --------------------------------------- | --------------------------------------------------------------------- |
| Embedded process / process call | `<subBpm>` / `<bpmCall>` | `<subProcess>` / `<callActivity>`       | Both formats distinguish scope from invocation                        |
| foreach                         | `<foreach>`          | `<multiInstanceLoopCharacteristics>`    | Sequential and deterministic ordered parallel modes                   |
| while loop                      | `<while>`            | `<standardLoopCharacteristics>`         | Equivalent bounded/conditional semantics                              |
| `break`                         | `<break>`                | not supported by BPMN 2.0               | **TBBPM-unique**                                                      |
| `continue`                      | `<continue>`             | not supported by BPMN 2.0               | **TBBPM-unique**                                                      |
| Parallel multi-instance         | `execution="parallel"`   | `isSequential="false"`                  | Durable-only; List input, bounded issue, optional ordered aggregation |

### 3.5 Other

| Feature             | TBBPM                                                                                                               | BPMN 2.0 (CompileFlow-supported subset)                                                                         | Notes                                                                                            |
| ------------------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Annotation          | `<note>`                                                                                                            | `textAnnotation` is rejected                                                                                    | TBBPM notes are design-time elements; the current BPMN subset does not preserve text annotations |
| Data object         | not supported                                                                                                       | not supported by CompileFlow subset                                                                             | Use process variables                                                                            |
| Message / signal    | ProcessEngine runtime has no broker semantics; Durable `waitEventTask` resumes one exact token-authorized Run occurrence | `receiveTask` and intermediate message catch lower to exact Durable Wait occurrences; signal nodes are rejected | CompileFlow does not provide general broadcast message/signal correlation                        |
| Deployment identity | Kept outside XML in `ProcessRef` and deploy metadata                                                                | Kept outside XML in `ProcessRef` and deploy metadata                                                            | Same engine contract                                                                             |

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
See [TBBPM Specification](tbbpm-specification.en.md) for the full contract.

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
TBBPM uses direct elements; BPMN keeps vendor data namespaced. BPMN `scriptTask` uses its standard
`scriptFormat` and `script` fields rather than wrapping the script in
`cf:action`. See the [BPMN Extension Specification](bpmn-extension-specification.en.md)
for the complete `cf:` contract.

## 5. When to choose TBBPM

Choose TBBPM when any of the following apply:

- You are starting a new flow on CompileFlow and have no BPMN-portability requirement.
- You need `break` or `continue`, which are not part of CompileFlow's BPMN subset.
- You prefer concise first-class action markup without BPMN extension containers.
- Your team is on the Alibaba/Taobao ecosystem where TBBPM tooling and examples already exist.

## 6. When to choose BPMN 2.0

Choose BPMN 2.0 when any of the following apply:

- You already own BPMN 2.0 definitions and their executable elements fit the CompileFlow subset.
- You need standard BPMN XML for model exchange, while accepting that executable extensions may require engine-specific
  adaptation.
- You are integrating with partners or external organizations that standardize on BPMN 2.0.
- You want to use BPMN 2.0 modelling tools and tutorials that already exist in the wider community.

> CompileFlow only executes the BPMN 2.0 subset listed above. Definitions using unsupported nodes such as user tasks,
> transactions, and choreographies fail parsing or `preflight` with a clear error. Parallel multi-instance is supported
> only by Durable execution; optional ordered aggregation uses CompileFlow's explicit output extensions. TBBPM `break`,
> `continue`, and built-in business attributes have no direct mapping to CompileFlow's BPMN subset.

## 7. Migrating between formats

### 7.1 TBBPM → BPMN 2.0

Direct mappings exist for: `start`/`end`, `autoTask`→`serviceTask`, `exclusive`→`exclusiveGateway`, `parallel`→
`parallelGateway`, `inclusive`→`inclusiveGateway`, `subBpm`→`subProcess`, `bpmCall`→`callActivity`, `foreach`→
`multiInstanceLoopCharacteristics`.

TBBPM `while` maps to BPMN `standardLoopCharacteristics`. No supported BPMN equivalent exists for `break` or
`continue`; those require restructuring the flow. Deployment identity does not affect format conversion because both
formats keep it outside XML.

### 7.2 BPMN 2.0 → TBBPM

Inverting the direct mappings is mechanical. BPMN
`multiInstanceLoopCharacteristics` maps to TBBPM `foreach`; BPMN
`standardLoopCharacteristics` maps to TBBPM `while`. Unsupported BPMN nodes such as user tasks,
transactions, and choreographies have no TBBPM equivalent and require a different runtime boundary.

## 8. Performance

Both TBBPM and BPMN run on the same compile-then-execute engine. First execution includes compilation; later executions
can reuse an exact generated runtime. The [benchmark guide](../../compileflow-benchmarks/README.md) records the current
JMH classes, setup boundaries, and reporting rules instead of duplicating that changing inventory here.

The benchmarks module measures CompileFlow and handwritten baselines only. It does not claim cross-product performance.

Run benchmarks locally with `./mvnw -pl compileflow-benchmarks verify -Pbenchmarks`.

## 9. Further reading

- [TBBPM Specification](tbbpm-specification.en.md) — formal TBBPM format reference
- [When to use CompileFlow](../when-to-use.md) — scope and anti-scope
- [Supported Surfaces](../architecture/06-SUPPORTED_SURFACES.en.md)
- [Extension Guide](../en/extension-guide.md)
