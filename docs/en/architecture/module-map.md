# CompileFlow Module Map

This page records CompileFlow's module boundaries and the main entry points for navigating the source tree.
Detailed behavior belongs in the linked specifications and API reference.

## Product Boundaries

| Module                                  | Responsibility                                                                                                       |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `compileflow-bom`                       | Maven dependency management for CompileFlow artifacts.                                                               |
| `compileflow-api`                       | Public `ProcessEngine`, `ProcessRef`, `ProcessDefinition`, result, configuration, and SPI contracts.                 |
| `compileflow-core`                      | Format-neutral resolution, semantic compilation, runtime caching, execution, and local routing.                      |
| `compileflow-tbbpm`                     | TBBPM parsing, validation, semantic frontend, and semantic-compiler provider.                                        |
| `compileflow-bpmn`                      | BPMN parsing, validation, semantic frontend, and semantic-compiler provider.                                         |
| `compileflow-spring-boot-autoconfigure` | Format-neutral Spring Boot composition for the core engine and `compileflow.engine.*`.                               |
| `compileflow-spring-boot-starter`       | Format-neutral Spring Boot and engine dependency aggregation.                                                        |
| `compileflow-spring-boot-starter-tbbpm` | ProcessEngine composition with the TBBPM frontend.                                                                   |
| `compileflow-spring-boot-starter-bpmn`  | ProcessEngine composition with the BPMN frontend.                                                                    |
| `compileflow-deploy`                    | Immutable publication, rollout control, deployment protocol, and node-local runtime convergence.                     |
| `compileflow-deploy-jdbc`               | Version-coupled JDBC persistence state machine shared by database providers.                                         |
| `compileflow-durable`                   | Durable parent module for explicit persisted execution, bounded Turns, effects, outbox work, and first-party stores. |
| `compileflow-durable-runtime`           | Preparation, recovery, leases, and Worker execution for Durable Runs.                                                |
| `compileflow-workbench-server`          | Workbench REST API, persistence, async invocation, deployment operations, and diagnostics.                           |
| `compileflow-workbench`                 | Browser authoring, execution preview, Learn, and Operate UI.                                                         |
| `compileflow-integration-tests`         | Cross-module behavior tests.                                                                                         |

The Maven reactor contains the Java modules. The Workbench is an independent pnpm workspace. Examples and benchmarks
are profile-gated auxiliary builds, not runtime dependencies.

## Execution Surfaces

`ProcessEngine` and `DurableProcessEngine` are sibling application-facing execution surfaces. `ProcessEngine` executes
one invocation from an explicit definition, version, or Alias. Durable creates a persistent Run; each Durable engine
owns its node-local resources and optional Workers. It is not a mode hidden inside `ProcessEngine`.

The core engine owns the `ProcessRuntimeResolver`, which resolves a bounded source, validates it, compiles the selected
format, and installs the resulting runtime under an exact local identity. `ProcessRuntimeManager` exposes the lifecycle
operations for node-local warm-up, exact Version binding, and unload; it is not publication or route authority.
Deploy composition uses `ProcessRuntimeOwnership`, `ProcessCallInspector`, and `ProcessExecutionGraphPreparer`
implementation capabilities for owner-aware installation, not the public `ProcessRuntimeManager` facade.

`LocalRoutingState` is the node-local serving state. `AliasAdmission` admits a published route, while
`DeterministicAliasSelector` applies the protocol-defined stable/candidate selection. Deployment's `DeploymentRuntime`
converges desired published state into locally ready runtimes; it does not alter the core engine contract.

## Ownership Rules

- Public contracts live in `compileflow-api` or the explicitly named product API module.
- Format modules parse and normalize their own source formats; core consumes their semantic-compiler provider boundary.
- Execution surfaces and persistence providers never depend on a source-format implementation. Starters compose these
  orthogonal axes without publishing format-by-execution products.
- Deployment control-plane state is committed before data-plane convergence and runtime installation.
- Durable persistence is explicit and independent from Workbench's whole-invocation async queue.
- Internal implementations remain package-private or module-internal unless a product contract requires exposure.
- A module must depend on the narrowest contract that expresses its responsibility; no catch-all utility module is used.

## Source Navigation

| Need                         | Start here                                                                            |
| ---------------------------- | ------------------------------------------------------------------------------------- |
| Public execution             | `compileflow-api` `ProcessEngine`                                                     |
| Runtime resolution           | `compileflow-core` `ProcessRuntimeResolver`                                           |
| Spring composition           | `CompileFlowEngineAutoConfiguration`                                                  |
| Alias routing                | `AliasAdmission`, `DeterministicAliasSelector`                                        |
| Deployment data plane        | `DeploymentRuntime` in `compileflow-deploy-runtime`                                   |
| Durable runtime              | `DurableProcessRuntimeManager` and the Durable runtime module                         |
| Browser draft storage        | `compileflow-workbench/apps/web/src/authoring/designer/api`                           |
| Workbench Server persistence | `compileflow-workbench-server/src/main/java/com/alibaba/compileflow/workbench/server` |
