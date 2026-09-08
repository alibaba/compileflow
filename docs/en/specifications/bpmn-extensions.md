# CompileFlow BPMN extension specification

The CompileFlow-owned XML vocabulary below applies to the supported BPMN 2.0 executable profile. It defines extension
syntax and execution-facing constraints. The supported standard BPMN elements are listed separately
in the [Node Support List](../node-support.md).

## 1. Namespace And Validation

CompileFlow extensions use this namespace:

```xml
xmlns:cf="http://www.compileflow.org"
```

Strict parsing validates two bundled schemas:

- [`BPMN20.xsd`](../../../compileflow-bpmn/src/main/resources/BPMN20.xsd) validates standard BPMN structure;
- [`CompileFlowBpmnExtensions.xsd`](../../../compileflow-bpmn/src/main/resources/CompileFlowBpmnExtensions.xsd)
  validates CompileFlow extension elements, attributes, and closed value sets.

Schema validation cannot express which extension belongs to which BPMN element or all code-generation constraints. The
BPMN parser and model validator therefore enforce the ownership and semantic rules in this specification after schema
validation. Unknown extension namespaces, unknown `cf:` elements, unsupported attributes, duplicate singleton
extensions, and child elements in leaf extensions are rejected rather than ignored.

The engine resolves schemas from its own classpath and disables external DTD and schema access. A BPMN file does not
need an `xsi:schemaLocation` hint to run.

When declared, expression `xsi:type` must resolve to `tFormalExpression` in the BPMN model namespace;
the prefix itself is arbitrary. Unknown or unbound namespaces are invalid even with schema validation disabled.
Explicit expression metadata is also validated on timer literals. A literal without expression metadata does not
require a Java language declaration. Singleton policies cannot be repeated across separate `extensionElements` containers.

## 2. Extension Ownership

Top-level extensions appear under the BPMN element's `extensionElements`. Action mappings and policies are direct
children of `cf:action`; a reconcile action is nested in its Effect policy.

| Extension             | BPMN owner                  |  Cardinality | Purpose                                                   |
| --------------------- | --------------------------- | -----------: | --------------------------------------------------------- |
| `cf:var`              | `process`                   | zero or more | Declares process variables.                               |
| `cf:input`/`output`   | `cf:action`                 | zero or more | Maps action inputs and its optional return value.         |
| `cf:input`/`output`   | `scriptTask`                | zero or more | Maps script inputs and its optional return value.         |
| `cf:input`/`output`   | `callActivity`              | zero or more | Maps called-process inputs and outputs.                   |
| `cf:action`           | `serviceTask`               |  exactly one | Declares the task implementation.                         |
| `cf:invocationPolicy` | `cf:action` or `scriptTask` |  zero or one | Governs synchronous timeout, retry, and terminal failure. |
| `cf:effectPolicy`     | `cf:action` or `scriptTask` |  zero or one | Governs recovery of an Effect.                            |

The qualified attributes `cf:collection`, `cf:item`, `cf:itemType`, `cf:index`,
`cf:target`, and `cf:source` belong directly to `multiInstanceLoopCharacteristics`; they are not
child extension elements. `cf:execution` belongs directly to `scriptTask`.

An extension on any other owner is invalid. `serviceTask` cannot be empty: its single `cf:action` is required by
preflight validation.

## 3. Variables

```xml
<extensionElements>
  <cf:var name="orderId"
          dataType="java.lang.String"
          inOutType="param"/>
</extensionElements>
```

| Attribute      | Required | Meaning                        |
| -------------- | -------: | ------------------------------ |
| `name`         |      yes | Generated Java variable name.  |
| `dataType`     |      yes | Java type name.                |
| `inOutType`    |      yes | `param`, `return`, or `inner`. |
| `defaultValue` |       no | Initial value.                 |
| `id`           |       no | XML identity metadata.         |
| `description`  |       no | Documentation.                 |

Process-variable names are unique and are used as generated fields and context-map keys. A root variable is a
declaration, not a mapping; directional `source` and `target` attributes exist only at action and process-call
boundaries.

For root variables, direction is an executable ownership contract: `param` is caller-owned admission input, `return`
is Process-owned output, and `inner` is Process-owned internal state. ProcessEngine `ProcessEngine.execute` and Durable
Start accept a closed, partial map of `param` variables only. `return`, `inner`, and undeclared keys are invalid rather
than silently ignored. ProcessEngine trigger-entry invocation is a distinct state-seed API and may accept any declared root
variable; it is not Durable continuation recovery.

An action input reads `source` and writes the action-local `target`; exactly one of `source` or `defaultValue` is
required. An action output has an implicit source (the action result) and writes `target`. A process call declares both
ends explicitly: inputs map caller `source` to called-process `target`, and outputs map called-process `source` to caller
`target`.

## 4. Actions

A `serviceTask` uses one canonical shape:

```xml
<cf:action type="spring-bean" execution="replayable" bean="orderService"
                   class="com.example.OrderService"
                   method="submit">
    <cf:input target="orderId"
            dataType="java.lang.String"
            source="orderId"/>

  <cf:invocationPolicy attemptTimeout="PT30S" maxAttempts="3"/>
</cf:action>
```

Each `serviceTask` has exactly one `cf:action`. Service Tasks invoke application-owned capabilities;
their `type` is exactly `java` or `spring-bean`.

| `type`        | Implementation contract                                                   |
| ------------- | ------------------------------------------------------------------------- |
| `java`        | `class`, optional `method`, and optional `cf:input`/`cf:output` mappings. |
| `spring-bean` | `bean`, `class`, optional `method`, and optional input/output mappings.   |

`cf:code` is reserved for a script `cf:reconcileAction` nested in an Effect recovery policy; it is not a
`serviceTask` implementation shape.

`execution` accepts `replayable` or `effect` and records the same Action-level Durable semantic as TBBPM. ProcessEngine
execution remains synchronous and does not interpret it; Durable requires it and lowers the Action to
a replayable step or governed Effect boundary. All values reachable from Process state are borrowed read-only. State
changes use declared outputs, and component/provider lifecycle owns concurrent invocation safety.

## 5. BPMN Script Tasks

A BPMN `scriptTask` uses standard BPMN fields for its implementation and CompileFlow extensions only for mappings:

```xml
<scriptTask id="calculate" scriptFormat="java">
  <extensionElements>
    <cf:input target="price" dataType="java.math.BigDecimal"
            source="price"/>
    <cf:output dataType="java.math.BigDecimal"
            target="total"/>
  </extensionElements>
  <script>return price.multiply(new java.math.BigDecimal("1.20"));</script>
</scriptTask>
```

`scriptFormat` and `script` must both be nonblank. Script Tasks own definition-local source; `scriptFormat` selects a
registered `ScriptExecutor`. Java and QL share this standard BPMN shape and the same semantic plan. Every script input
and output is explicitly declared with `cf:input` or `cf:output`.

Java Code is a method body. The first-party executor creates a typed wrapper during Process runtime load and
compiles it with `javac --release 17`; the `ScriptProgram` belongs to that exact disposable runtime and is never
persisted process identity. Exact language, source, and declared signature stay bound to the immutable Process version
so a runtime can prepare again from source. Java Code accepts JDK platform input/output types only and runs as trusted
embedded computation, not a sandbox; Workbench deployments for untrusted authors require an isolated Code Runner.

CompileFlow execution controls attach directly to the Script Task: `cf:execution` is a qualified task attribute, while
`cf:invocationPolicy` and `cf:effectPolicy` are direct children of `extensionElements`.
`cf:execution` defaults to `replayable`; Effectful scripts declare `cf:execution="effect"`. A `serviceTask` must never
contain a script action.

## 6. Invocation Policy

```xml
<cf:action type="java" class="com.example.OrderService" method="submit">
  <cf:invocationPolicy timeout="PT2M"
                 attemptTimeout="PT30S"
                 maxAttempts="3"
                 initialBackoff="PT1S"
                 backoffMultiplier="2.0"
                 maxBackoff="PT10S"
                 jitter="full"
                 retryOn="transient"
                 onFailure="propagate"/>
</cf:action>
```

| Attribute           | Default                | Constraint                                                             |
| ------------------- | ---------------------- | ---------------------------------------------------------------------- |
| `timeout`           | no timeout             | Positive ISO-8601 duration for the complete invocation.                |
| `attemptTimeout`    | no timeout             | Positive ISO-8601 duration for one attempt; no greater than `timeout`. |
| `maxAttempts`       | `1`                    | Integer from `1` through `100`; includes the initial invocation.       |
| `initialBackoff`    | `PT1S` when retrying   | Nonnegative ISO-8601 duration with whole-millisecond precision.        |
| `backoffMultiplier` | `1.0`                  | Finite number at least `1.0`.                                          |
| `maxBackoff`        | `100 * initialBackoff` | Nonnegative ISO-8601 duration with whole-millisecond precision.        |
| `jitter`            | `full`                 | `full` or `none`.                                                      |
| `retryOn`           | `always`               | `never`, `transient`, `always`, or a registered retry policy name.     |
| `onFailure`         | `propagate`            | `propagate`, `continue`, or a registered failure policy name.          |

`timeout` includes every attempt and retry backoff. Each attempt receives the smaller of `attemptTimeout` and the
remaining overall budget. Attempt timeout may retry; overall timeout never retries and is resolved through `onFailure`.
No attempt starts after the overall deadline. Cancellation is cooperative and cannot roll back an external side effect.
The policy governs one synchronous action invocation; it is not a Durable job or Effect policy. Authors must apply it only to omitted or
`replayable` execution whose implementation tolerates retry or uncertain timeout. An `effect` Action cannot declare
`cf:invocationPolicy`; Local invokes it once synchronously, while Durable materializes a committed Effect occurrence.

## 7. Durable Effect Policy

An Action with `execution="effect"` may contain one `cf:effectPolicy` inside its `cf:action`, after the mappings. Omitting the policy
means immediate manual review after one uncertain attempt. Manual recovery declares no automatic recovery settings.
`recovery="retry"` requires `maxAttempts` (`2..100`) and a positive `recoveryDelay` no greater than 24 hours.
`recovery="reconcile"` requires `maxAttempts` (`1..100`), `maxReconcileAttempts` (`1..1000`), a positive
`recoveryDelay`, and exactly one `cf:reconcileAction`. For retry or reconcile, optional `maxRecoveryDuration` is positive and
no greater than 30 days. A reconcile Action is a recovery query adapter and cannot declare `execution` or another Effect
policy. It has no output, default values, or invocation policy. Each input `source` names a field in the persisted
original Effect request, and `target` names the reconcile invocation argument; `source` is not a Process expression.
The only additional source is the stable Kernel metadata field `__cf_effect_id`. An Action input target populated from
that metadata field is not itself a persisted request field. Attempt numbers are runtime telemetry and cannot be mapped
into Action or reconcile business arguments. The return value is an `EffectReconcileOutcome`, and a confirmed result is
mapped through the original Effect Action's output.

For per-occurrence bounds in a structured collection, `cf:effectPolicy` may instead use
`recoveryPlanVariable="variableName"`. The visible variable must declare
`com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan`; static recovery attributes must be absent. The selected
closed value is validated and frozen when the Effect occurrence is issued. A static `cf:reconcileAction` is still required
when the selected value can use `RECONCILE`.

## 8. Called Process

```xml
<callActivity id="price" calledElement="pricing.calculate"
              cf:version="v3">
  <extensionElements>
    <cf:input target="request" source="request"/>
    <cf:output source="price" target="price"/>
  </extensionElements>
</callActivity>
```

`calledElement` is the canonical called-process code. Exactly one of `cf:classpath` or `cf:version` is required.
`cf:classpath` is an exact, canonical resource path from the application classpath; it is not a URI and does not
support schemes, parent traversal, wildcard lookup, or caller-relative resolution. Direct definitions may call either
Classpath or exact-Version targets. Exact-Version definitions and published artifacts may call only exact
called-process Versions inherited under the caller's namespace. Alias is root-admission only. The transitive graph is prepared before
business execution, and Durable recovery persists exact process-target and call-site identities instead of rereading
current sources or routes.
Durable executes the call as another `ProcessInvocation` frame in the same Run; it does not create an independently
addressable Run. The exact called process's `param` and `return` declarations are the type and direction authority, so
called-process mappings do not declare `dataType`. An input selects exactly one of `source` or `defaultValue`; an output
requires both called-process `source` and caller `target`.

## 9. Multi-Instance Attributes

```xml
<multiInstanceLoopCharacteristics isSequential="true"
    cf:collection="items"
    cf:item="item"
    cf:itemType="com.example.Item"
    cf:index="index"/>
```

Sequential multi-instance execution is supported on both execution surfaces; parallel multi-instance is Durable-only
and ProcessEngine execution rejects it. `cf:collection` and `cf:item` are required Java identifiers. The collection
must name a declared process variable or an enclosing multi-instance variable.
`cf:index` is optional; iteration-local names must not collide with process or enclosing lexical variables.
`cf:itemType`, when present, must be a Java class name; it defaults to `java.lang.Object`. The collection variable
must declare an `Iterable` or array. When the collection declaration has exactly one direct type argument, that
argument must be compatible with `cf:itemType`; other declarations are checked value by value at runtime.
Parallel execution narrows the input contract to a List-compatible declaration. Optional output aggregation is declared
by the atomic pair `cf:target` and `cf:source`: the former references a declared List-compatible
Process variable, and the latter references a declared `inner` Process variable. The names must be distinct, and a type
argument on the output target List must exactly match the output source variable type. Aggregated results are ordered by input
index. Sequential aggregation is published atomically when the loop exits; omitting both attributes runs every
iteration without collecting a result. Before each iteration, the output source variable is reset to its declared
Process-variable default.

## 10. Canonical Write-Back

The BPMN writer preserves the executable subset and writes CompileFlow data in the `cf:` namespace.
Source attributes outside the canonical schema are rejected. Write-back is
not a generic BPMN document round trip: unsupported BPMN elements and vendor extensions are rejected before a model can
be serialized.
