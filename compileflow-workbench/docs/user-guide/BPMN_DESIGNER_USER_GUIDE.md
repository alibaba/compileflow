# BPMN Designer User Guide

The Workbench BPMN designer creates and edits local BPMN drafts for CompileFlow's executable BPMN subset. Drafts remain
in the current browser until you export them or explicitly publish through the server-backed Operate workflow.

## Create Or Open A Process

1. Open the Build workspace at `/build`.
2. Create a BPMN process, open a saved process, or start from a template.
3. Workbench opens the canonical designer route at `/build/designer`.

The route records the source and model type. Do not edit route query parameters to change an existing process's format.

## Supported Nodes

| Category    | Nodes                                   |
| ----------- | --------------------------------------- |
| Events      | Start event, end event                  |
| Tasks       | Service task, script task, receive task |
| Gateways    | Exclusive, parallel, inclusive          |
| Composition | Call activity, embedded subprocess      |

This is a bounded CompileFlow subset, not every BPMN 2.0 element. Unsupported XML is reported during import or
validation. Workbench rejects `userTask` because the CompileFlow runtime does not provide durable human-task semantics.

An embedded subprocess owns a nested graph with its own start and end event. Workbench preserves that hierarchy in
visual and XML views. Sequence flows cannot cross the container boundary, event subprocesses are unsupported, and a
receive task cannot be nested because trigger invocations do not restore an embedded call stack.

Service tasks use CompileFlow `cf:action` extensions rather than Camunda execution attributes. Script tasks default to
the built-in `qlexpress` executor and may name another executor installed in the engine. Process variables, action and
call-activity variable mappings, message definitions, plain-text documentation, and diagram geometry are retained across
XML and visual editing.

## Editing

- Drag nodes from the palette onto the canvas.
- Connect compatible ports to create sequence flows.
- Select a node or edge to edit its properties.
- Configure conditions on outgoing gateway flows and identify the default path where needed.
- Place subprocess children inside their container and keep all internal sequence flows within that boundary.
- Use the visual, XML, or split view for the same underlying process.
- Review validation findings before saving or exporting.

XML edits are parsed back into the structured model. If parsing fails, correct the reported error before switching back
to visual editing.

## Save, History, And Export

Saving writes the draft to local IndexedDB. Local history stores snapshots for the current flow. Restoring a
snapshot loads its XML back into the editor; save the flow to persist the restored draft.

Workspace export produces a JSON backup containing local flows, snapshots, and templates. BPMN XML export
produces an engine-facing process definition. These formats serve different purposes and are not interchangeable.

Browser storage is origin-specific and can be cleared by browser policy or user action. Use workspace export when the
local draft must be retained or moved to another device.

## Runtime Boundary

The designer does not make a draft executable by itself:

- Learn/designer trial execution uses the loopback development mock in mock mode and Workbench Server preview in real
  mode.
- Browser simulation stops at an embedded subprocess; use real backend preview for generated-code subprocess execution.
- Real validation, release, routing, and execution belong to `compileflow-workbench-server` and the Java engine.
- Operate state is not copied into the local workspace.

## Keyboard Shortcuts

| Shortcut                                 | Action                      |
| ---------------------------------------- | --------------------------- |
| `Ctrl/Cmd + S`                           | Save                        |
| `Ctrl/Cmd + Z`                           | Undo                        |
| `Ctrl/Cmd + Y` or `Ctrl/Cmd + Shift + Z` | Redo                        |
| `Ctrl/Cmd + C` / `Ctrl/Cmd + V`          | Copy / paste selected nodes |
| `Delete`                                 | Delete selected elements    |
| `Ctrl/Cmd + Plus` / `Ctrl/Cmd + Minus`   | Zoom in / out               |
| `Ctrl/Cmd + 0`                           | Fit canvas                  |
| `Ctrl/Cmd + F`                           | Search                      |
| `Ctrl/Cmd + /`                           | Shortcut help               |

The in-product shortcut dialog is authoritative when platform key conventions differ.

## Troubleshooting

| Symptom                           | Check                                                                   |
| --------------------------------- | ----------------------------------------------------------------------- |
| Flow cannot be saved              | Browser IndexedDB availability and the displayed storage error          |
| XML cannot be imported            | Root definitions/process structure and the first parser finding         |
| A connection is rejected          | Source/target node compatibility and duplicate or self-loop rules       |
| A flow validates but does not run | Java engine supported-surface rules and executable action configuration |
| Real execution is unavailable     | Vite real-mode target and `compileflow-workbench-server` health         |

See the [Workbench documentation index](../README.md) for deployment and developer references.
