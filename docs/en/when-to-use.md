# When to use CompileFlow

CompileFlow is an embeddable JVM process engine for business logic that changes less often than it runs. It accepts
TBBPM or the supported BPMN subset, prepares an executable runtime, and exposes separate surfaces for direct execution,
versioned deployment, persisted execution, and visual authoring. Those surfaces solve different problems and must be
selected explicitly.

## Choose the execution surface first

| Requirement                                  | Use                               | Important boundary                                                             |
| -------------------------------------------- | --------------------------------- | ------------------------------------------------------------------------------ |
| Execute trusted definitions in process       | `ProcessEngine`                   | No crash-safe continuation                                                     |
| Publish immutable versions and route Aliases | CompileFlow Deploy                | Controls versions and traffic; does not persist Run continuation               |
| Resume supported processes after restart     | CompileFlow Durable               | PostgreSQL and MySQL are supported; custom Stores use the Provider Preview SPI |
| Persist and retry a whole Workbench request  | Workbench Server async invocation | Retries the complete Engine call; it is not Durable                            |
| Model and inspect supported definitions      | Workbench                         | Authoring and operations UI; the Java engine remains execution authority       |

## Good fits

- High-frequency business rules such as pricing, inventory checks, eligibility, order validation, and fraud signals.
- Teams that need one reviewable process definition shared by business and engineering users.
- JVM applications that need a typed boundary through `ProcessEngine.execute(...)` and `ProcessResult<T>`.
- Flows that benefit from immutable versions, revision-checked Alias routing, deterministic canaries, and auditable
  rollout history.
- Automation-focused TBBPM definitions, including `while`, `break`, `continue`, and Java, Spring bean, or script
  actions.
- Supported TBBPM or BPMN flows that must persist waits, timers, nested calls, cancellation, and external operations
  modeled as Effects.
- Spring Boot applications that want an application-scoped engine and bounded observability.

## Poor fits

- **Full human-task management.** CompileFlow does not provide assignment, candidate groups, claim/complete, forms,
  escalation, delegation, or approval history.
- **Full BPMN 2.0 execution semantics.** CompileFlow executes a documented subset. Human tasks,
  transactions, choreographies, and event-based gateways are outside the supported profile; `cf:` extensions are
  CompileFlow-specific.
- **A unique definition for nearly every call.** Compilation is amortized over reuse; ad hoc one-shot definitions can
  spend more time preparing than executing.
- **DMN decision tables.** CompileFlow does not include a DMN engine.
- **A non-JVM runtime.** Runtime execution requires Java 17 or later and the `jdk.compiler` module.

## Integration boundaries

- CompileFlow owns automated in-process execution; applications own any human-task lifecycle around it.
- `ProcessEngine.trigger(...)` starts a new invocation from an application-owned event. It does not resume a stored
  CompileFlow Run.
- Durable owns the Run and exact Wait occurrence; the application owns assignment and authorization.
  Treat `WaitToken` as a one-time credential; never place it in logs, metric labels, browser URLs, or third-party
  metadata.
- Decision tables and distributed coordination remain application-owned concerns.

## Decision checklist

| Question                                                       | If yes                                    | If no                                          |
| -------------------------------------------------------------- | ----------------------------------------- | ---------------------------------------------- |
| Does each definition execute many times?                       | Compilation cost can be amortized         | Compilation reuse may not pay off              |
| Must execution resume from a persisted boundary after restart? | CompileFlow Durable                       | `ProcessEngine` may be sufficient              |
| Is retrying the complete request sufficient?                   | Workbench Server async invocation may fit | Durable is required for persisted continuation |
| Do you need managed human tasks or the full BPMN standard?     | Outside the supported scope               | Continue evaluation                            |
| Do you need immutable versions and canary routing?             | Add CompileFlow Deploy                    | Use direct Inline or Classpath definitions     |
| Can the application run on Java 17+ with `jdk.compiler`?       | Continue evaluation                       | Unsupported runtime environment                |

## Related guides

- [Supported surfaces](architecture/supported-surfaces.md)
- [Quick start](quick-start.md)
- [Process format reference](specifications/process-formats.md)
- [Durable process](durable-process.md)
