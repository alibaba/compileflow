# BPMN Designer Developer Guide

The BPMN designer is built into CompileFlow Workbench and is not published as a standalone React component library.
This guide covers its implementation and extension points.

## Scope

The BPMN designer provides:

- X6-based visual editing.
- Node, edge, action, loop, and invocation-policy properties.
- XML import, synchronized XML editing, and export.
- Local workspace persistence and snapshots.
- Undo, redo, clipboard, search, validation, and canvas controls.

The designer authors only the CompileFlow-supported BPMN subset.

## Code Entry Points

| Area                       | Path                                                                     |
| -------------------------- | ------------------------------------------------------------------------ |
| Designer composition       | `apps/web/src/authoring/designer/components/BpmnDesigner.tsx`            |
| Canvas adapter             | `apps/web/src/authoring/designer/components/BpmnCanvas.tsx`              |
| Palette                    | `apps/web/src/authoring/designer/components/BpmnNodePalette.tsx`         |
| Property panel             | `apps/web/src/authoring/designer/components/BpmnPropertiesPanel.tsx`     |
| Node type union            | `apps/web/src/authoring/designer/types/bpmnNodeTypes.ts`                 |
| XML codec                  | `apps/web/src/authoring/designer/serialization/bpmnXmlCodec.ts`          |
| Validation entry           | `apps/web/src/authoring/designer/validation/BpmnValidator.ts`            |
| Shared topology validation | `apps/web/src/authoring/designer/validation/ProcessTopologyValidator.ts` |
| Editor state               | `apps/web/src/authoring/designer/store/`                                 |

## Supported Elements

The palette and `BpmnNodeType` define ten node types:

| Category    | Types                                   |
| ----------- | --------------------------------------- |
| Events      | Start event, end event                  |
| Tasks       | Service task, script task, receive task |
| Gateways    | Exclusive, parallel, inclusive          |
| Composition | Call activity, embedded subprocess      |

Sequence flows support conditions and default-flow semantics. Activity loop settings are limited to the
CompileFlow-supported standard-loop and sequential or parallel multi-instance behavior, including optional ordered output.
Parallel multi-instance requires Durable execution; Workbench's ProcessEngine preview rejects that runtime capability.

Only the elements listed above are editable. `userTask` is rejected because the runtime has no durable human-task model.
Embedded `subProcess` is supported: normalized nodes carry `parentId`, the XML codec recurses through nested containers,
and validation gives each container its own start/end and connectivity boundary. Event subprocesses, cross-container
sequence flows, and trigger entries inside a subprocess are rejected.

The in-browser simulator stops when it reaches an embedded subprocess. Use Workbench Server preview to verify compiled
execution; simulation does not flatten or approximate nested control flow.

Service tasks serialize CompileFlow `cf:action` extensions. Script tasks default to the built-in `qlexpress` executor
while retaining explicit custom executor names. The XML codec also round-trips process variables, action and
call-activity mappings, message definitions, plain-text
documentation, BPMN DI geometry, and sequence-flow waypoints.

The authoritative engine surface is documented in
[`../../../docs/en/architecture/supported-surfaces.md`](../../../docs/en/architecture/supported-surfaces.md). The
designer must reject or warn about XML outside that surface instead of presenting unsupported elements as executable.

## State And XML

Redux owns the normalized editing state and undo stack. X6 is a rendering and interaction adapter, not a second domain
model. Parser and writer functions translate between XML and structured editor state.

Keep these rules:

- Update structured state from property panels and graph interactions.
- Generate XML through the format writer.
- Preserve parser warnings and surface them to the user.
- Never patch XML with regular-expression or substring replacement.
- Expose a graph instance only through the development-only debug handle when
  `VITE_COMPILEFLOW_DEBUG=true`; never create format-specific global handles.

## Action And Expression Safety

Service-task and script-task configuration must map to the executable subset accepted by the Java engine. Client-side
simulation uses the allowlisted interpreter in
[`safeExpressionEvaluator.ts`](../../apps/web/src/authoring/designer/simulation/safeExpressionEvaluator.ts); it must not use
`eval`, `new Function`, DOM access, or arbitrary JavaScript execution.

The browser simulation is explanatory only. Java engine validation and execution remain authoritative.

## Validation

Validation covers:

- Required start/end structure.
- Supported node and connection types.
- Reachability and topology.
- Gateway conditions and defaults.
- Required action/script fields.
- Supported loop configuration.

User-visible messages use i18n keys. Tests should assert stable finding codes and severity rather than translated prose.

## Adding Or Changing A Node Type

Keep these parts synchronized when adding or changing a node type:

1. Type union and property types.
2. Palette and X6 registration.
3. Property configuration and editor.
4. XML parser and writer.
5. Validation rules.
6. i18n labels.
7. Unit tests and relevant E2E coverage.
8. Supported-surface documentation when engine behavior changes.

Expose a node in the palette only after the Java parser, validator, generator, and runtime support it.

## Verification

Run from `compileflow-workbench/`:

```bash
pnpm --filter @compileflow/workbench-web type-check
pnpm --filter @compileflow/workbench-web lint
pnpm --filter @compileflow/workbench-web test
pnpm --filter @compileflow/workbench-web test:e2e:smoke
```

Use `pnpm verify:delivery` for the Workbench delivery gate.
