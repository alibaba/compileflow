# Process Model Architecture

TBBPM and BPMN definitions enter the CompileFlow compiler through the ownership boundaries and invariants below. For
individual XML elements and attributes, use the format specifications.

## 1. Authority And Scope

Each question has one primary reference:

| Question                              | Reference                                                                                                  |
|---------------------------------------|------------------------------------------------------------------------------------------------------------|
| TBBPM syntax and execution semantics  | [TBBPM Specification](../specs/tbbpm-specification.en.md)                                                  |
| TBBPM schema                          | [`TBBPM.xsd`](../../compileflow-tbbpm/src/main/resources/TBBPM.xsd)                                        |
| BPMN extension syntax and semantics   | [BPMN Extension Specification](../specs/bpmn-extension-specification.en.md)                                |
| BPMN extension schema                 | [`CompileFlowBpmnExtensions.xsd`](../../compileflow-bpmn/src/main/resources/CompileFlowBpmnExtensions.xsd) |
| Supported TBBPM and BPMN nodes        | [Node Support](../en/node-support.md)                                                                      |
| Differences between the formats       | [TBBPM vs BPMN](../specs/tbbpm-vs-bpmn.md)                                                                 |
| Parser and writer registration parity | [`check_spec_impl_parity.py`](../../scripts/check_spec_impl_parity.py)                                     |

The XML schemas reject malformed document structure. Parsers, model validators, the structured-plan analyzer, and Java
compilation enforce the remaining semantic constraints. A file passing XSD validation alone is not necessarily
executable.

## 2. Module Ownership

`compileflow-core` owns the format-neutral compiler and runtime contracts:

- source loading and size limits;
- secure XML parsing support;
- the internal control-flow model;
- structured graph analysis;
- Java source generation and in-memory compilation;
- runtime executors for actions, gateways, loops, and subprocess calls.

`compileflow-tbbpm` and `compileflow-bpmn` own their XML models, parsers, validators, writers where supported, node
generators, and engine providers. Their implementation model classes are not public application contracts.

Both format modules register `ProcessEngineProvider` through Java
`ServiceLoader`. Applications select the format explicitly through
`ProcessEngineFactory` or `compileflow.engine.model-type`.

## 3. Compilation Pipeline

```mermaid
flowchart LR
    A["ProcessDefinition"] --> B["Bounded source loader"]
    B --> C["Secure XML parser"]
    C --> D["Format model"]
    D --> E["Model validation"]
    E --> F["StructuredControlFlowAnalyzer"]
    F --> G["Format node generators"]
    G --> H["Java source"]
    H --> I["javac --release 17"]
    I --> J["Isolated generated class"]
    J --> K["Process runtime cache"]
```

The same pipeline is used by preflight, source generation, warm-up, and first execution. These entry points differ in
how far they proceed, not in their interpretation of the process definition.

Generated classes implement the internal process runtime contract. They are cache artifacts owned by the engine and are
not an API for applications to instantiate or persist.

## 4. Format Boundaries

### TBBPM

TBBPM is CompileFlow's compact native format. A `.bpm` document uses explicit nodes and transitions, CompileFlow action
handles, typed variables, and CompileFlow action policies. The XSD and the TBBPM specification define its accepted
shape.

The TBBPM module supports canonical write-back for the elements registered in
`TbbpmElementWriterRegistry`. Parser and writer registration must remain aligned so a supported model does not
silently lose executable content.

### BPMN

The BPMN module accepts a documented executable subset of BPMN 2.0. Standard elements retain their BPMN namespace and
meaning; CompileFlow-specific action and policy data live under the `cf:` extension namespace.

General BPMN validity does not imply CompileFlow executability. Unsupported collaboration, compensation, or
human-workflow elements fail validation instead of being ignored. The browser designer may expose a smaller authoring
subset than the Java parser; the node-support document records both boundaries.

### Cross-format Invocation Policy

TBBPM and BPMN extensions use the same generated action-execution contract:

- an action has at most one policy element;
- `maxAttempts` is an integer, `1..100`, including the initial invocation;
- `backoffMultiplier` is a finite double, `>= 1.0`;
- every configured interval is a non-negative, whole-millisecond ISO-8601 duration;
- Timeout cancellation is cooperative: the engine requests interruption and does not start a retry while the previous
  call is still known to be running;
- JVM fatal errors (`Error`) are never retried or converted to
  `onFailure=continue`.

Timeout or retry can make an external result ambiguous, so the implementation must tolerate repeated invocation and
uncertain completion. This synchronous policy does not classify Durable semantics: `execution=replayable|effect` is the
separate Action-level contract, and externally observable work belongs behind a governed Durable Effect boundary.
Exact fields and defaults remain normative in the [TBBPM specification](../specs/tbbpm-specification.en.md) and
[BPMN extension specification](../specs/bpmn-extension-specification.en.md).

## 5. Structured Control Flow

CompileFlow generates structured Java rather than interpreting an arbitrary token graph. `StructuredControlFlowAnalyzer`
therefore validates and lowers the graph before any node generator emits code.

A gateway's role follows its graph shape:

- one incoming and multiple outgoing transitions is a split;
- multiple incoming and one outgoing transition is a join;
- a gateway with multiple incoming and multiple outgoing transitions is rejected; model a join followed by a separate
  split;
- branching from a non-gateway node is rejected.

TBBPM does not need separate `fork` and `join` element names. The join is still an explicit gateway node in the graph;
only its role is derived from incoming and outgoing transitions. Parallel and inclusive splits must converge at a
matching gateway type unless the process terminates. This keeps the XML model small without making synchronization
implicit.

The analyzer uses dominator and post-dominator relationships to identify each branch region and its deterministic
convergence. It assigns every executable node to one branch body or one continuation. Shared continuation code is
emitted once after convergence, not copied into every branch. Unreachable nodes, cross-container edges, ambiguous joins,
overlapping regions, and unsupported nested concurrent regions fail preflight.

## 6. Generated Execution

The immutable `StructuredControlFlowPlan` is passed to the format-specific node generators. Exclusive gateways emit one
selected branch. Parallel and inclusive gateways delegate branch coordination to `GatewayExecutor`.

Concurrent branches execute against generated branch frames rather than writing through one shared generated-process
object. The analyzer validates variable access before generation, and the executor applies the documented merge and
failure rules. The user-facing concurrency behavior is documented in
[Advanced Features](../en/advanced-features.md).

Root-variable direction defines ownership. ProcessEngine execution and Durable Start accept only a closed, partial
map of declared `param` variables; `return` and `inner` remain Process-owned and undeclared keys fail before application
code runs. A ProcessEngine trigger entry is intentionally different: it starts a new downstream invocation from a partial seed
of declared root state. Durable completion never uses that state-seed path; it resumes the committed semantic
checkpoint and applies the typed occurrence result.

Wait and event-entry nodes compile into trigger entry points. In the local engine the caller owns any continuation.
The optional Durable product instead persists Process-owned semantic checkpoints and committed Wait/Timer/Effect facts;
it does not persist generated Java, bytecode, application implementation, or provider compatibility.

## 7. Changing A Process Model

A node or attribute change is incomplete until all affected layers agree:

1. update the normative specification and the format schema (`TBBPM.xsd` or
   `CompileFlowBpmnExtensions.xsd`);
2. update the format model and parser;
3. update canonical write-back when the format supports it;
4. update model and structured-control-flow validation;
5. update the node generator and runtime helper, if needed;
6. add parse, write-back, invalid-topology, generated-source, compile, and execution tests as applicable;
7. update the English and Chinese node-support and process-model documents;
8. run `python3 scripts/check_spec_impl_parity.py`.

Do not add parser-only elements, silently ignored attributes, or generator branches that bypass structured-plan
ownership.

## 8. Related Documents

- [TBBPM Specification](../specs/tbbpm-specification.en.md)
- [BPMN Extension Specification](../specs/bpmn-extension-specification.en.md)
- [Execution Flow](04-EXECUTION_FLOW.en.md)
- [Supported Surfaces](06-SUPPORTED_SURFACES.en.md)
- [Contributing](../../CONTRIBUTING.md)
