# CompileFlow Node Support List

The ProcessEngine without persisted continuation `ProcessRuntime` and the opt-in Durable execution surface support different BPMN and TBBPM
elements. A flow element is executable only when its selected surface has both a semantic model and an implementation.
Support on one surface does not imply support on the other.

## TBBPM Supported Nodes

### Basic Nodes

- `start` - Start node
- `end` - End node
- `autoTask` - Auto task node
- `scriptTask` - Script task node

### Gateway Nodes

- `exclusive` - Exclusive gateway
- `parallel` - Parallel gateway
- `inclusive` - Inclusive gateway

### Process Control

- `while` - Bounded conditional loop
- `foreach` - Sequential or parallel collection loop
- `subBpm` - Embedded BPM scope
- `bpmCall` - Call another BPM definition
- `continue` - Continue node (used within loops)
- `break` - Break node (used within loops)

Both sequential and parallel `foreach` support ordered output aggregation. Each iteration starts with the output
element variable reset to its declared default; the collection is published only when the loop exits.

### Trigger Entry Nodes

- `waitTask` - Wait task
- `waitEventTask` - Wait event task

On the `ProcessEngine`, these are top-level named entries for new `trigger(...)` invocations, not durable
checkpoints, and are not valid inside loops. Under `durable-strict@1`, they are persisted Wait boundaries and
may occur inside bounded loops; resumption requires the exact Run's one-time Wait token.

### Durable Boundaries And Action Semantics

- `timerTask` - Persists a suspension until a literal duration, duration expression, or absolute wake-time expression
  becomes due.

`timerTask` is accepted by the TBBPM schema and Durable compiler, including inside loops; ProcessEngine
execution rejects it because `ProcessRuntime` has no durable scheduler. Effect is not a node. Every executable Action in a Durable
model explicitly declares `execution="replayable|effect"`. An Effect Action uses its ProcessEngine Java, bean, inline, or
registered script implementation while the Kernel durably owns dispatch and unknown-outcome handling.

The Durable TBBPM profile supports `start`, `end`, `autoTask`, `scriptTask`, `exclusive`, both Wait nodes, `timerTask`,
`while`, `foreach`, `break`, `continue`, structured `parallel`/`inclusive`, `subBpm`, and `bpmCall`. Parallel and Inclusive use persisted
deterministic frontiers and stable merge order; a process call is excluded from a concurrent region because its
application writes cannot be proven branch-local. Durable `bpmCall` declares an exact application-classpath location
in a Direct graph, or an exact child Version. Exact-Version graphs use Version-only dependencies. Alias is
resolved only for the root admission, and every static
call-site binding remains exact. The call executes as another `ProcessInvocation` frame inside the same Run; it does not
create a Child Run.
Exclusive-gateway, While, Timer, guard, and transition expressions remain generated Java source. Action types can
indirectly use a registered `ScriptExecutor`; CompileFlow does not fix one script language. The source-neutral Durable
While plan accepts an optional `maxIterations` guard, but TBBPM requires it on every `while`. Durable Turn budgets
bound one execution slice independently of that source-language rule.

See the [TBBPM specification](../specs/tbbpm-specification.en.md#34-durable-timer-and-effect-actions)
and [Durable Process guide](durable-process.md).

### Others

- `note` - Note node

## BPMN 2.0 Supported Nodes

### Events

- `startEvent` - Start event
- `endEvent` - End event
- `intermediateCatchEvent` with `messageEventDefinition` - Durable Wait boundary selected by the referenced message
  name.
- `intermediateCatchEvent` with `timerEventDefinition` - Durable Timer boundary using a literal duration, duration
  expression, or absolute wake-time expression. `timeCycle` is not supported.

### Tasks

- `serviceTask` - Service task. Requires exactly one `cf:action`; action mappings are nested in
  `cf:action`, not attached to the task.
- `scriptTask` - Script task. Requires `scriptFormat` and nonblank standard `<script>` content; mapped `cf:var` elements
  are attached directly to the task.
- `receiveTask` - Requires `messageRef`, which must resolve to exactly one top-level `message` definition with a
  nonblank name. On `ProcessEngine` runtime it is a named entry for a new invocation; on Durable it is an exact
  persisted Wait occurrence. It has no `inAction`/`outAction` hooks; model explicit tasks when work is required before
  or after the boundary.

### Gateways

- `exclusiveGateway` - Exclusive gateway
- `parallelGateway` - Parallel gateway
- `inclusiveGateway` - Inclusive gateway

### Structures

- `subProcess` - Embedded subprocess. Each subprocess must be a connected graph with exactly one start event and one end
  event. Trigger entry nodes such as `receiveTask` are supported only in the root process because a new `trigger(...)`
  invocation does not restore an embedded call stack.
- `callActivity` - Call activity. Requires `calledElement` and exactly one of `cf:classpath` or `cf:version`; mapped
  `cf:var` elements are attached directly and return mappings must name target process variables.

> **Workbench execution boundary:** the BPMN designer preserves and edits the full `subProcess`
> hierarchy, including nested nodes and transitions, across visual and XML round trips. The
> in-browser simulator fails closed for embedded subprocess execution; use the
> backend execution mode for generated-code and runtime semantics.

### Definition Metadata

- `message` - Message definition metadata. It can be parsed and preserved on the BPMN model, but it is not a standalone
  executable node.

### Activity Wrappers

- `standardLoopCharacteristics` - Executes a conditional or bounded standard loop. `loopMaximum`, when present, is
  an integer from `1` through `2147483647`; at least `loopCondition` or `loopMaximum` is required.
- `multiInstanceLoopCharacteristics` - Executes sequential or deterministic ordered parallel `cf:collection`
  iteration. Parallel mode is Durable-only and requires a List-compatible input. Optional ordered aggregation uses the
  atomic `cf:target` / `cf:source` pair in either mode; sequential results are published when the
  loop exits. Generated lexical names must be valid Java identifiers, and `cf:itemType` must be a valid Java
  class name.

Executable BPMN requires a nonblank `targetNamespace`, one process with `isExecutable="true"`, globally unique ids, and
only supported attributes and extension data. Unknown executable data is rejected instead of being discarded during
canonical write-back. The BPMN process `id` is its CompileFlow process code and must exactly match the code carried by
`ProcessDefinition`. The complete `cf:` syntax and ownership rules are defined by the
[BPMN Extension Specification](../specs/bpmn-extension-specification.en.md).

## Removed Nodes

The following BPMN 2.0 elements are not supported by CompileFlow and their definition classes have been removed from the
codebase. CompileFlow does not provide the product lifecycle or broadcast/collaboration infrastructure required by
these elements. Files containing them fail parsing or preflight; the engine never deploys them with silently missing
behavior.

### Removed — Architecture-Incompatible

These elements require runtime infrastructure that CompileFlow does not provide:

- `userTask` — Requires a persistent task store with claim/complete lifecycle. Use a durable external task system; use
  TBBPM `waitTask` + `trigger` only when starting a new invocation at a named entry is sufficient.
- `manualTask` — Has the same persistence constraint as `userTask`.
- `businessRuleTask` — Requires a decision table engine. Use `serviceTask` with a Java action instead.
- `sendTask` — Requires a messaging system with send/receive semantics. Use `serviceTask` instead.
- `boundaryEvent` — Requires per-instance activity state tracking for error/compensation/timer boundaries.
- `intermediateThrowEvent` — Requires an event dispatcher with active subscribers.
- `signal` — Requires a signal registry and runtime subscriptions (stateful).
- Event definitions (`cancelEventDefinition`, `compensateEventDefinition`, `conditionalEventDefinition`,
  `errorEventDefinition`, `escalationEventDefinition`, `linkEventDefinition`, `signalEventDefinition`,
  `terminateEventDefinition`) — All require event infrastructure that
  CompileFlow does not provide.

### Removed — Collaboration Domain Not Applicable

CompileFlow executes single-process flows; collaboration/choreography elements are out of scope:

- `choreography`, `choreographyTask`, `subChoreography`
- `callConversation`, `conversation`, `subConversation`
- `globalBusinessRuleTask`, `globalConversation`, `globalManualTask`, `globalScriptTask`, `globalUserTask`
- `partnerEntity`, `partnerRole`, `participantAssociation`, `participantMultiplicity`

### Removed — Persistence/Resource Layer Not Applicable

The ProcessEngine runtime keeps invocation state in memory, and the Durable Store persists only CompileFlow's own execution
records. Neither execution surface implements BPMN data-store or resource-assignment semantics; applications own those
concerns:

- `dataStore`, `dataStoreReference`, `dataAssociation`, `dataInputAssociation`
- `loopDataInputRef`, `loopDataOutputRef`
- `resource`, `resourceRole`, `potentialOwner`, `performer`, `humanPerformer`
- `assignment`, `resourceParameter`, `resourceParameterBinding`, `resourceAssignmentExpression`

### Removed — Other

- `transaction` — Use Spring `@Transactional` at the service layer.
- `complexGateway` — Requires event-condition evaluation infrastructure.
- `eventBasedGateway` — Requires event subscription registry.
- `group`, `textAnnotation`, `association` — Diagram-only artifacts with no runtime semantics.
- `auditing`, `monitoring` — Observability is handled via external APM systems.
- `category`, `categoryValue` — Diagram grouping; no runtime effect.
- `correlationProperty`, `correlationSubscription`, `correlationKey` — Message correlation requires a message broker.
- `error`, `escalation`, `itemDefinition`, `interface`, `operation`,
  `ioParameter`, `inputSet`, `outputSet`, `inputOutputSpecification`, `inputOutputBinding` — Metadata for execution
  semantics CompileFlow does not implement.
- `lane`, `laneSet` — Process organization; no runtime effect in compile-then-execute model.
- `multiInstanceFlowCondition` — Multi-instance is handled via `multiInstanceLoopCharacteristics`.
- `complexBehaviorDefinition` — Behavior monitoring infrastructure.
- `endPoint`, `import`, `relationship`, `rendering` — Schema-level metadata with no runtime effect.

## Alternative Solutions

### Human Task Implementation

An external task system can own task identity, persistence, authorization, and variables, then use a TBBPM trigger
entry:

```xml
<waitTask id="approval" name="Wait for Approval" g="80,0,120,48">
    <transition to="afterApproval"/>
</waitTask>
```

```java
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessDefinition.classpath("approval.flow", "flows/approval.flow.bpm"),
        ProcessTrigger.at("approval"),
        approvalData);
```

This starts a new execution at the entry. CompileFlow does not store or resume the previous invocation.

### Durable human-in-the-loop orchestration

For a persisted Run, model the human step as a `waitTask`. The Durable kernel commits the Wait and its
`WAIT_COMMITTED` Outbox event. An external task service may use the event to create and manage its own task,
including assignment, authorization, forms, comments, and SLA policy. After the external service authorizes and
deduplicates the decision, it completes the exact Wait occurrence through `DurableProcessEngine.completeWait(...)`.

```java
durable.completeWait(waitToken, Map.of("approved", true));
```

`waitToken` is a bearer capability and must be protected like a credential. CompileFlow provides human-in-the-loop
orchestration, not an engine-native `humanTask` node or Human Task Management service.

### ProcessEngine Runtime Scheduled Task Implementation

For ProcessEngine execution, call the process through an external scheduling system such as Spring Scheduler or Quartz. For a
persisted delay within one Durable Run, use `timerTask` instead:

```java
@Scheduled(fixedDelayString = "${jobs.scheduled-flow.delay:PT1M}")
public void scheduledTask() {
    engine.execute(ProcessDefinition.classpath("scheduled.flow", "flows/scheduled.flow.bpm"), Map.of()).orElseThrow();
}
```

Fixed delay prevents one scheduler instance from overlapping its own executions. Distributed deployments still need an
external single-owner or idempotency policy if only one cluster-wide invocation is allowed.

## Source Of Truth

The runtime support boundary is defined by these provider classes:

- TBBPM parser registry: `TbbpmElementParserRegistry`
- TBBPM semantic frontend: `TbbpmSemanticFrontend`
- BPMN parser registry: `BpmnElementParserRegistry`
- BPMN semantic frontend: `BpmnSemanticFrontend`
- ProcessEngine compiled realization: `JavaProcessCodeGenerator`
- Durable realization boundary: `DurableMachineLowerer`

Parser presence alone is not enough. Some elements, such as BPMN `message` and loop characteristics, are metadata or
wrappers rather than standalone runtime nodes. Durable-only semantics use `DurableMachineLowerer` instead of the
ProcessEngine runtime realization. When adding node support, update the parser, source validator, semantic frontend,
applicable eligibility/lowering/realization, tests, and this document together.
