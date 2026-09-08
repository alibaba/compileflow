# TBBPM specification

> **Schema**: `compileflow-tbbpm/src/main/resources/TBBPM.xsd`.
> **Runtime baseline**: Java 17 or later.

## 1. Scope

TBBPM is CompileFlow's compact XML format for compiled business orchestration. A definition is parsed, semantically
validated, and compiled to Java bytecode. The same model can target one of two explicit execution surfaces:

- `ProcessEngine`, which executes one Process invocation; or
- `DurableProcessEngine`, which persists a Run at supported Wait, Timer, Effect, and terminal boundaries.

TBBPM does not provide a built-in human-task product or arbitrary BPMN message correlation.
`timerTask` is a Durable-only node and ProcessEngine execution rejects it. Effect is an execution semantic on a
process action, not a separate node. A ProcessEngine
`waitTask`/`waitEventTask` is a named entry for a later, independent
`trigger(...)` invocation; under the Durable strict profile it is a persisted Run boundary with a one-time Wait token.
Applications must choose the execution surface explicitly.

The accepted contract has three layers:

1. `TBBPM.xsd` defines XML structure and basic attribute types.
2. The TBBPM parser resolves nodes and transitions into a model.
3. Model validation and strict preflight enforce graph, loop, action, and compilation semantics.

Unknown elements and invalid attributes are rejected. They are not silently ignored.

## 2. Document

The root element is `<bpm>`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpm code="greeting.flow" name="Greeting" description="Build a greeting">
    <var name="name" dataType="java.lang.String" inOutType="param"/>
    <var name="greeting" dataType="java.lang.String" inOutType="return"/>

    <start id="start" name="Start" g="0,0,32,32">
        <transition to="greet"/>
    </start>
    <scriptTask id="greet" name="Build greeting" g="80,0,120,48">
        <action type="script" language="qlexpress">
            <input source="name" target="name" dataType="java.lang.String"/>
            <output target="greeting" dataType="java.lang.String"/>
            <code>'Hello, ' + name</code>
        </action>
        <transition to="end"/>
    </scriptTask>
    <end id="end" name="End" g="240,0,32,32"/>
</bpm>
```

### 2.1 Root attributes

| Attribute     | Required | Meaning                                                                          |
| ------------- | -------: | -------------------------------------------------------------------------------- |
| `code`        |      yes | Process code. Namespace, version, and alias belong to `ProcessRef`, not the XML. |
| `name`        |       no | Display name.                                                                    |
| `description` |       no | Human-readable description.                                                      |

The root does not carry deployment version, tenant, alias, or a triggerability mode flag. A model is triggerable when it
contains a top-level `waitTask` or `waitEventTask`. The XML `code` must exactly equal the code carried by its
`ProcessDefinition`. Process codes are canonical identifiers of at most 128 characters: they start with an ASCII letter
or digit and contain only ASCII letters, digits, `.`, `_`, or `-`.

### 2.2 Variables

```xml
<var name="orderId"
     dataType="java.lang.String"
     inOutType="param"
     defaultValue=""
     description="Order identifier"/>
```

| Attribute      | Required | Meaning                        |
| -------------- | -------: | ------------------------------ |
| `name`         |      yes | Generated Java variable name.  |
| `dataType`     |      yes | Java type name.                |
| `inOutType`    |      yes | `param`, `return`, or `inner`. |
| `defaultValue` |       no | Initial value.                 |
| `description`  |       no | Documentation.                 |

Process variable names must be unique across all three directions. A root variable's `name` is both its generated Java
field name and its process-context map key. Root variables are declarations, not mappings; directional `source` and
`target` attributes exist only at action and process-call boundaries.

The three directions are an executable ownership contract: `param` is caller-owned admission input, `return` is
Process-owned output, and `inner` is Process-owned internal state. ProcessEngine `ProcessEngine.execute` and Durable Start
accept a closed, partial map of `param` variables only; a `return`, `inner`, or undeclared key is invalid rather than
silently ignored. An omitted parameter retains its definition default, while a present key with a null value is an
explicit null. ProcessEngine trigger-entry invocation is a separate state-seed API and may accept any declared root variable;
it is not Durable continuation recovery.

## 3. Nodes And Transitions

Every executable or diagram node requires an `id`; the geometry attribute `g` is optional. Node IDs must be globally unique across the
complete model, including nested loops. `name` and
`description` are optional unless a node definition below says otherwise.

A transition is always nested in its source node:

```xml
<transition to="next" condition="amount &gt; 100" name="highValue"/>
```

`to` is required; `condition`, `name`, and `g` are optional. Outgoing transitions are evaluated in declaration order.
TBBPM does not accept a transition `priority`, a root-level transition, or a `from` attribute. A target must resolve
inside the transition's current node container. Every executable node must be reachable from that container's
configured start; diagram-only `note` nodes are excluded from this reachability rule.

### 3.1 Start And End

```xml
<start id="start" name="Start" g="0,0,32,32">
    <transition to="task"/>
</start>
<end id="end" name="End" g="240,0,32,32"/>
```

A process has exactly one `start` and one `end`. The start has exactly one outgoing transition. The end is the unique
control-flow exit and has no outgoing transition.

### 3.2 Tasks

`autoTask` and `scriptTask` execute exactly one `<action>` and continue. `autoTask` accepts application-owned Java
class or Spring Bean actions. `scriptTask` accepts only workflow-owned `Script` actions. A Script is
workflow-owned code with explicit inputs and an optional explicit output; it is not a synonym for an interpreted
language. Use graph transitions and gateways for control flow instead of empty task nodes.

```xml
<autoTask id="load" name="Load order" g="80,0,120,48">
    <action type="java" execution="replayable" class="com.example.OrderService" method="load">
            <input target="orderId" dataType="java.lang.String"
                 source="orderId"/>
            <output dataType="com.example.Order"
                 target="order"/>

    </action>
    <transition to="end"/>
</autoTask>
```

Action concurrency and Durable execution are independent:

- `concurrency="forbidden|safe"` declares whether sibling branches may invoke the action at the same time. The
  fail-closed default is `forbidden`.
- `execution="replayable|effect"` is an optional `<action>` attribute for ProcessEngine models and is required for every
  executable Action in a Durable model. `replayable` runs inside a Segment and may be repeated after an uncommitted
  attempt. `effect` creates a committed Effect boundary before dispatch.

An optional `<invocationPolicy>` governs synchronous invocation retry/timeout behavior; see section 6. It is valid on
omitted or `replayable` execution only, when the implementation tolerates retry and uncertain timeout. An `effect`
Action cannot declare it. Local execution invokes an Effect once synchronously and does not provide crash recovery;
Durable execution materializes the same Action as a committed Effect occurrence governed by `<effectPolicy>`.

### 3.3 Wait Entries

`waitTask` and `waitEventTask` are trigger entries selected by their node IDs:

```xml
<waitTask id="approval" name="Approval" timeout="PT24H" g="80,0,120,48">
    <transition to="end"/>
</waitTask>

<waitEventTask id="payment" name="Payment"
               event="payment.completed" timeout="PT24H" g="80,80,120,48">
    <transition to="end"/>
</waitEventTask>
```

Both nodes use the globally unique, non-blank node `id` as their trigger selector and may declare an optional
non-negative `timeout`. `waitEventTask` additionally requires an `event`; `waitTask` does not have an event attribute.

When ProcessEngine execution reaches a wait entry, that invocation ends. A later `trigger(...)` starts a new execution at
the matching trigger-entry node ID. Its input must contain all state needed by the remaining flow. For
`waitEventTask`, the requested event must match. Wait nodes have no `inAction` or `outAction`; authors use
explicit task nodes when work is required on either side.

On the `ProcessEngine`, this is an entry-point mechanism, not durable instance resume or message correlation;
Wait entries must be top-level and timed Waits are rejected because Local has no timer authority. Under the Durable strict
profile, reaching the node commits a persisted Wait occurrence. `DurableProcessEngine.completeWait(...)` durably
resolves that exact occurrence using the one-time token emitted with `WAIT_COMMITTED`; an independently managed
Durable Worker subsequently advances the eligible Run.
Durable Wait entries may appear inside a loop; a token is valid only for the exact loop occurrence that created it.

### 3.4 Durable Timer And Effect Actions

`timerTask` suspends a Durable Run until its due time, compared against the database authority clock:

```xml
<timerTask id="cooldown" name="Cooldown" duration="PT15M" g="80,0,120,48">
    <transition to="charge"/>
</timerTask>
```

Exactly one schedule attribute is required:

| Attribute            | Contract                                                                                              |
| -------------------- | ----------------------------------------------------------------------------------------------------- |
| `duration`           | Non-negative ISO-8601 `java.time.Duration`, such as `PT15M`.                                          |
| `durationExpression` | Deterministic expression evaluated from persisted process state; its result is a duration.            |
| `wakeAtExpression`   | Deterministic expression evaluated from persisted process state; its result is an absolute wake time. |

A relative duration is anchored to the Store scheduling time. An absolute `wakeAtExpression` preserves the supplied
instant, including when it is earlier than scheduling: it is immediately eligible for the next Timer-resolution sweep,
not synchronously completed by Start. The resolved time cannot precede either scheduling or the due instant.

A Process task becomes a Durable Effect boundary through its Action:

```xml
<autoTask id="charge" name="Charge" g="240,0,120,48">
    <action type="spring-bean" execution="effect" bean="paymentAction"
                      class="com.example.PaymentAction" method="charge">
            <input target="orderId" dataType="java.lang.String"
                 source="orderId"/>
            <input target="effectId" dataType="java.lang.String"
                 source="__cf_effect_id"/>
            <output dataType="java.lang.String"
                 target="receiptId"/>

        <effectPolicy recovery="retry"
                      maxAttempts="3"
                      recoveryDelay="PT5S"/>
    </action>
    <transition to="end"/>
</autoTask>
```

The task and Action remain the business model; no second Effect target identity is introduced. The application runtime
resolves and invokes the Action implementation. The Kernel commits the occurrence and portable input before dispatch,
passes the stable occurrence ID as invocation key, records the result, and resumes from the same task. A `param` mapping
must name its source process variable or default; a `return` mapping must target a declared root process variable.

If `<effectPolicy>` is absent, the conservative recovery mode is `manual`: one dispatch followed by review if the outcome
is unknown. Explicit recovery modes are `manual`, `retry` (redispatch with the same Effect ID), and `reconcile` (a
recovery-only `reconcileAction` first proves completed/not-executed/still-unknown). Retry/reconcile may map the stable
`__cf_effect_id` to an Action input when the business invocation needs it; this mapping is not itself an idempotency
proof. The policy owns only Process semantics such as bounded attempts, recovery delay, and maximum recovery duration. Operational polling, leases, and
missing-provider backoff remain Runtime concerns.

A `reconcileAction` contains only an invocation and zero or more inputs. Each input's `source` names a field in the
persisted original Effect request, while `target` names the reconcile invocation argument. `source` is a field identity,
not a Process expression. The only additional source is the stable Kernel metadata field `__cf_effect_id`. An Action
input target populated from that metadata field is not itself a persisted request field. Attempt numbers are runtime
telemetry and cannot be mapped into Action or reconcile business arguments. Defaults, outputs, execution settings,
invocation policy, and nested Effect policy are invalid.
The reconcile return value is an `EffectReconcileOutcome`; a confirmed result is mapped by the original Effect Action's
output declaration.

For a structured collection whose occurrences require different recovery bounds, `effectPolicy` may instead declare
`recoveryPlanVariable="variableName"`. The visible variable must have type
`com.alibaba.compileflow.durable.api.effect.EffectRecoveryPlan`. Static recovery attributes are then forbidden; the
Machine validates the closed value before issuing the occurrence, and the Store freezes it with that occurrence. If the
value may select `RECONCILE`, the policy must also declare the single static `reconcileAction` capability.

Timer and Effect boundaries are valid inside loops. ProcessEngine execution fails closed on `timerTask` and timed Wait;
an Effect Action is invoked once synchronously without Durable recovery. An untimed top-level Wait remains a
ProcessEngine trigger entry for a later independent invocation. See the
[Durable Process guide](../durable-process.md) for registration and runtime semantics.

### 3.5 Gateways

TBBPM supports:

- `exclusive`: exclusive split or join.
- `parallel`: parallel split or join.
- `inclusive`: execute every matching outgoing branch.

```xml
<exclusive id="route" name="Route" g="80,0,80,48">
    <transition to="premium" condition="amount &gt;= 1000"/>
    <transition to="normal"/>
</exclusive>
```

An exclusive or inclusive split may have at most one default transition, represented by an outgoing transition without
`condition`. Parallel transitions cannot be conditional. Every condition expression must have a javac type of `boolean`
or `java.lang.Boolean`. Only
`true` matches; `false` and a nullable `Boolean` value of `null` do not. The same truth contract applies to
`while` conditions and conditional `break`/`continue` nodes. Conditions must be side-effect free: they must not mutate
process variables, invoke effectful services, or depend on evaluation count. The engine may evaluate them in model
order, but evaluation is not a committable process action.

One gateway element represents either a split or a join. Its role is derived entirely from topology; TBBPM has no
`next`, pairing identifier, or separate fork/join node types:

- a split has one incoming and at least two outgoing transitions;
- a join has at least two incoming and exactly one outgoing transition;
- a mixed gateway with multiple incoming and outgoing transitions is rejected and must be expressed as a join followed
  by a split;
- a `parallel`/`inclusive` split reaches shared continuation through an explicit join of the same type, unless every
  branch terminates directly;
- an `exclusive` emits one token, so it may use a ProcessEngine implicit merge or an explicit exclusive join;
- every executable node must be reachable, each container has one control-flow exit, and crossing or partially merged
  unstructured regions are rejected before code generation.

Gateways are pure routing and synchronization elements and cannot contain actions. Computation or side effects that must
happen before routing or after synchronization belong to an explicit task. This keeps execution, retry, idempotency, and
observability ownership attached to one business step. A join gateway also cannot declare a condition on its sole
outgoing transition because it evaluates neither a node body nor routing. Conditional routing requires a separate split
after the join. Validation rejects invalid definitions instead of silently skipping actions or conditions.

Every value reachable from Process state is borrowed read-only by actions, scripts, expressions, and called processes.
Process state changes exist only through declared outputs and explicit Process constructs. Component resolvers and script
providers may be invoked concurrently across branches and Process invocations; their lifecycle owner is responsible for
instance isolation or thread safety. External effects still require the appropriate Effect protocol because a branch
failure cannot roll them back.

On entry to a concurrent region, an `inclusive` split evaluates all conditions against the parent flow state in model
order before any active branch starts. Each active branch runs on an isolated, typed branch frame. Later nodes in that
branch can observe its earlier writes, while sibling branches cannot. Only after every active branch succeeds does the
parent thread commit each branch's declared root-variable write set in model order. A branch, cancellation, or
submission failure publishes no branch process-variable writes. Concurrent return mappings must target root process
variables directly; nested property, collection-element, and array-element writes are rejected. This guarantee covers
publication to the in-memory parent flow only, not database, message, or RPC side effects.

Branch frames copy typed field values rather than arbitrary object graphs, so reachable object references remain
read-only. The analyzer rejects only cross-branch writes to the same root Process variable; a read in one branch and a
declared write in another are isolated by the fork-time snapshot. Mutable lists, maps, arrays, entities, and POJOs must
not be modified in place. Aggregation should use distinct branch outputs followed by a serial post-join calculation,
never completion order, a shared collection, or last-write-wins.

### 3.6 Embedded BPM and BPM Calls

```xml
<subBpm id="validation" name="Validation" g="80,0,220,120">
    <start id="validationStart">
        <transition to="validate"/>
    </start>
    <autoTask id="validate">
        <!-- action -->
        <transition to="validationEnd"/>
    </autoTask>
    <end id="validationEnd"/>
    <transition to="next"/>
</subBpm>
```

`subBpm` defines a nested scope inside the current BPM. It has no independent process identity, source locator,
version, call-site binding, or parameter mapping. It shares the enclosing process state and must contain exactly one
direct `start` and one direct `end`; all direct executable children must be reachable from that start. Nested `subBpm`,
`while`, and `foreach` containers are allowed. The semantic frontend lowers this structure to the same scope boundary used by
BPMN `subProcess`; it does not create a child process invocation. When the scope is nested inside a loop, it may contain
`break` and `continue`; those controls bind to the nearest enclosing loop rather than to the `subBpm` itself.

```xml
<bpmCall id="validate" name="Validate" code="order.validate"
         classpath="flows/order-validate.bpm" g="80,0,120,48">
    <input target="orderId" source="orderId"/>
    <output source="validatedOrder" target="validatedOrder"/>
    <transition to="end"/>
</bpmCall>
```

`bpmCall` invokes another BPM definition. `code` is required and follows the same canonical process-code rules as the
root definition. Exactly one of `classpath` or `version` is required. `classpath` is an exact, canonical resource path
from the application classpath; it is not a URI and does not support schemes, parent traversal, wildcard lookup, or
caller-relative resolution. On the `ProcessEngine`, the called process runs synchronously. Nested process variables map caller context to and from the
called process and therefore support only `param` and `return`. To pause a ProcessEngine caller after the call, connect the
`bpmCall` to an explicit `waitTask` or `waitEventTask`; process invocation and external correlation are
separate semantics.

A call input declares only `source` or `defaultValue`, plus the called-process `target`; the exact called process's
`param` declaration is the sole authority for its type. A call output declares the called process's `return` variable as `source` and a declared caller
variable as `target`. Process-call mappings never declare `dataType`.

A direct definition may call either an exact Classpath definition or an already loaded exact Version. An exact-Version definition may call
only exact Versions, and published artifacts enforce the same Version-only dependency rule. Version targets inherit the
caller's namespace. Alias is root-admission only and is never inherited by nested calls. The complete transitive graph is
resolved and retained before the first business action, and runtime dispatch uses the exact call-site binding rather
than resolving by code.

Under `durable-strict@1`, a process call pushes an exact called-process invocation frame into the same Run; it does not
create an independently addressable Run. The continuation persists exact process-target identities and call-site
bindings, so recovery resumes the retained binding without Alias rerouting.
Successful completion applies declared return mappings; failure becomes an explicit Process-owned caller failure. `bpmCall`
remains prohibited inside a concurrent region because the invoked Process write/effect set cannot be proven branch-local.

### 3.7 Loops

TBBPM represents the two different iteration intents as two different structured nodes: `while` for conditional
repetition and `foreach` for collection traversal. There is no generic loop node or discriminator attribute. Each
loop body must contain exactly one direct `start` and one direct `end`; all direct executable body nodes must be reachable from the
start, and the end must not have an outgoing transition. The loop node's own `transition` leaves the container after the
iteration completes.

Conditional loop:

```xml
<while id="retryLoop" name="Retry"
           condition="attempt &lt; maxAttempts"
           maxIterations="10" index="attempt" g="80,0,180,140">
    <start id="retryStart"><transition to="try"/></start>
    <autoTask id="try" name="Try">
        <action type="spring-bean" bean="retryService" class="com.example.RetryService" method="tryOnce"/>
        <transition to="retryEnd"/>
    </autoTask>
    <end id="retryEnd"/>
    <transition to="end"/>
</while>
```

`condition` and the `maxIterations` safety bound (`1..2147483647`) are required. `index` is an optional zero-based lexical
counter visible only within the body. If the condition remains true after the bound is reached, execution fails instead
of silently truncating the loop. The bound is a TBBPM source-language requirement, not a Durable scheduler limit.

Collection loop:

```xml
<foreach id="itemsLoop" name="Items" collection="items"
             item="item" itemType="com.example.Item"
             index="index" g="80,0,180,140">
    <start id="itemsStart"><transition to="handle"/></start>
    <autoTask id="handle" name="Handle">
        <action type="spring-bean" bean="itemService" class="com.example.ItemService" method="handle">
                <input target="item" dataType="com.example.Item"
                     source="item"/>

        </action>
        <transition to="itemsEnd"/>
    </autoTask>
    <end id="itemsEnd"/>
    <transition to="end"/>
</foreach>
```

`collection`, `item`, and `itemType` are required. `index` is optional and `execution` is either
`sequential` (the default) or Durable-only `parallel`. The item and index variables are lexical locals: they must be
distinct valid Java identifiers and cannot shadow process variables or enclosing loop locals. `collection` must name
an `Iterable` or array variable, and `itemType` must be a valid Java class name. When the collection declaration has
exactly one direct type argument, that argument must be compatible with `itemType`; other collection declarations
remain valid and every snapshotted value is checked against `itemType` at runtime. Parallel execution narrows the
input contract to a `java.util.List`-compatible declaration so input indexes remain stable across recovery.

An optional atomic `output` child declares indexed result aggregation:

```xml
<foreach id="itemsLoop" collection="items" item="item"
             itemType="com.example.Item">
    <output target="results" source="itemResult"/>
    <start id="itemsStart"><transition to="handle"/></start>
    <autoTask id="handle"><!-- effect action --><transition to="itemsEnd"/></autoTask>
    <end id="itemsEnd"/>
    <transition to="end"/>
</foreach>
```

When present, `output` must be the first direct child of `foreach`. Both attributes are required and reference
distinct declared process variables. `target` must declare `java.util.List`, not a concrete List implementation;
aggregation guarantees an ordered List, not a particular implementation or mutability. `source` must name an
`inner` process variable, and a declared target type argument must exactly match the source variable type. Raw
output lists remain valid. Parallel aggregation is deterministic by input index, never completion order. Empty input
produces an empty output target without creating iteration work. Sequential aggregation follows iteration order and
is published atomically when the loop exits; a terminating `break` includes the current iteration's result. Before each
iteration, the output source variable is reset to its declared process-variable default, so an early `continue` cannot
reuse the preceding iteration's result.

`break` and `continue` may appear in sequential loop bodies, with an optional condition expression. `continue` is also
valid per iteration in parallel `foreach`; `break` is forbidden there because a single iteration cannot cancel its
already-issued siblings deterministically. They may be nested through embedded `subBpm` scopes and always bind to the
nearest enclosing loop; a nested loop therefore owns its own controls. Sequential loops may be nested. A parallel
collection loop may be nested
under a sequential loop, but no parallel loop or concurrent gateway may be nested beneath an already-concurrent scope.
ProcessEngine trigger entries and Parallel/Inclusive concurrent splits are not valid loop children. Durable sequential
loops may contain Wait, Timer, Effect Action, and structured Parallel/Inclusive scopes. Process calls remain forbidden in
all concurrent scopes. Loop and control expressions retain the generated-Java expression contract.

Parallel iteration issuance uses a bounded rolling window controlled by
`compileflow.durable.worker.max-active-iterations` (`1..64`, default `32`). This value controls Runtime scheduling
policy and is not persisted as Process identity. The input is frozen once, sibling iteration state is isolated, crash
recovery keeps stable index identity, and configured output aggregation always follows input index rather than completion
order. The continuation stores the parent state once and only each active iteration's state delta. Empty input creates no
iteration work and, when output is configured, produces an empty output collection.

### 3.8 Note

`note` is non-executable diagram metadata and cannot participate in transitions:

```xml
<note id="note1" name="Review" comment="Manual review happens externally"
      g="80,80,180,40"/>
```

## 4. Actions

All actions use one canonical XML shape:

```xml
<action type="java" class="com.example.Handler" method="run"/>
```

Supported built-in action types are:

| `type`        | Required action content                                                            | Meaning                                                       |
| ------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `java`        | `class`, optional `method` (default `execute`), optional `input`/`output` children | Invoke an application-owned reusable Java capability.         |
| `spring-bean` | `bean`, `class`, optional `method` (default `execute`), optional mappings          | Invoke an application-owned Spring capability.                |
| `script`      | `language`, one `code`, optional mappings                                          | Evaluate workflow-owned code through a named Script executor. |

`script` is the only protocol action type for dynamic code. Its `language` selects a registered `ScriptExecutor`.
QLExpress and Java Code share the same XML and semantic plan; any additional language must use that contract. Exclusive,
while, timer, and transition guards
remain generated Java source and do not use a script executor.

Every Script input and output is declared with `<input>` or `<output>`. Java Code is a method body, for example
`return price.multiply(quantity);`. During Process runtime load, the first-party Java executor generates a typed wrapper
and compiles it with `javac --release 17`; the resulting `ScriptProgram` belongs to that exact disposable runtime and is
never persisted. The language, exact source, and declared signature remain immutable Process-version truth, so a runtime
can always prepare it again from source. Java Code accepts JDK platform input/output types only and runs as trusted embedded
computation, not a sandbox; an untrusted Workbench deployment requires an isolated Code Runner with network disabled by
default.

Script example:

```xml
<action type="script" language="qlexpress">
    <input source="price" target="price" dataType="java.math.BigDecimal"/>
    <input source="quantity" target="quantity" dataType="java.lang.Integer"/>
    <output target="total" dataType="java.math.BigDecimal"/>
    <code>price * quantity</code>
</action>
```

Java Code example:

The trusted in-process Java executor is provided by core; QL and Java are available by default.

```xml
<action type="script" language="java">
        <code><![CDATA[return java.time.Instant.now().toString();]]></code>

</action>
```

## 5. Action Mapping

Every mapping uses one direction: `source` is read, then the value is written to `target`.

An action input is `<input source="processExpression" target="argument" dataType="java.lang.String"/>`.
`target` and `dataType` are required. Exactly one of `source` or `defaultValue` is required. Source expressions are
preserved exactly; `target` is the action-local argument or script binding.

An action output is `<output target="processVariable" dataType="java.lang.String"/>`. Its source is the action's
single return value and is therefore implicit. Both attributes are required, only one output may be declared, and its
target must be a declared root process variable. Omitting the output discards the return value.

Process calls use the same algebra but both sides are explicit: call inputs map caller `source` to called-process `target`, while
call outputs map called-process `source` to caller `target`. Call mappings do not repeat `dataType`; the called process's `param`
and `return` declarations are authoritative.

## 6. Invocation Policy

```xml
<action type="java" class="com.example.OrderService" method="submit">
  <invocationPolicy timeout="PT2M"
           attemptTimeout="PT30S"
           maxAttempts="3"
           initialBackoff="PT1S"
           backoffMultiplier="2.0"
           maxBackoff="PT10S"
           jitter="full"
           retryOn="transient"
           onFailure="propagate"/>
</action>
```

Durations use canonical uppercase ISO-8601 notation with whole-millisecond precision. `timeout` is the positive wall-clock
budget for the entire logical invocation, including every attempt and retry delay. `attemptTimeout` is the positive
budget for one attempt and must not exceed `timeout` when both are present. Each attempt receives the smaller of its
attempt budget and the remaining invocation budget. `maxAttempts` is between 1 and 100 and includes the initial
invocation. Backoff multiplier is at least 1.0.
`jitter` is `full` (the default) or `none`; full jitter uniformly distributes each delay from zero through its
capped exponential backoff.
An attempt timeout may be retried according to `retryOn`; an overall timeout never retries and is resolved through
`onFailure`. No attempt starts after the overall deadline, and retry delay consumes the overall budget. Cancellation is
cooperative and cannot undo external effects. `retryOn` and `onFailure` use exact lowercase kebab-case names configured
on the ProcessEngine. A timeout or retry does not turn an Action into a
Durable Effect and cannot prove exactly-once behavior in an external system. Authors must use only implementations whose
semantics tolerate the selected ProcessEngine policy. Durable rejects non-default invocation policies instead of silently ignoring them. It uses
`execution="replayable|effect"` plus optional `<effectPolicy>` instead.

## 7. Validation And Execution

`ProcessPreflightOptions.fast()` resolves, parses, validates the schema, and applies model semantics. It catches, among
other failures:

- Missing or duplicate node IDs and process variables.
- Missing start/end nodes or invalid gateway shape.
- Unresolved transitions and executable nodes unreachable from their container start.
- Missing loop attributes, invalid iteration bounds, malformed loop scopes, and invalid `break`/`continue` placement.
- Trigger entries nested inside loops for ProcessEngine execution, and concurrent splits nested inside parallel `foreach` for
  Durable execution. Durable sequential loops may own structured concurrent splits.

`ProcessPreflightOptions.strict()` additionally builds and compiles the runtime. It therefore checks generated Java,
registered script executors, Java types, and method-facing syntax.

Execution resolves one exact definition byte snapshot, builds a runtime identity from that content and local compilation
inputs, and executes the selected runtime (`COMPILED` by default, or `INTERPRETED` over the same semantic plan).
Repeated requests may reuse that exact runtime. Triggered execution uses the
same source, routing, runtime, result, and observability pipeline as ProcessEngine execution.

```java
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "payment.flow", "flows/payment.flow.bpm"),
        ProcessTrigger.on("payment", "payment.completed"),
        Map.of("paymentId", "P-42"));
```

The call starts a new invocation at `payment`. It does not restore a previous Java object or stored process instance.

## 8. Conformance

A conforming CompileFlow TBBPM implementation:

- Accepts only XML admitted by the bundled XSD and semantic validator.
- Rejects unknown elements, invalid attributes, unresolved transitions, and unsupported actions.
- Uses `foreach` and `while` as the only loop nodes, without a generic loop discriminator.
- Keeps ProcessEngine trigger entries at the top level; Durable Waits may occur inside supported scopes and loops.
- Compiles generated Java with release 17.
- Does not give ProcessEngine invocations persisted continuation; only the explicit Durable surface owns Run recovery.
- Does not provide general message correlation or an engine-native human-task lifecycle.

## 9. Related Documents

- [Process Model Architecture](../architecture/process-model.md)
- [Process format reference](process-formats.md)
- [Java API Reference](../api-reference.md)
- [Node Support](../node-support.md)
