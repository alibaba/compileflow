# BPMN Designer User Guide

The Workbench BPMN designer creates and edits BPMN drafts for CompileFlow's executable BPMN subset. Build workspace
drafts are stored in the current browser; drafts opened from Operate remain bound to the Server.

## Create Or Open A Process

1. Open the Build workspace at `/build`.
2. Create a BPMN process, open a saved process, or start from a template.
3. Workbench opens the designer at `/build/designer`.

## Supported Nodes

| Category    | Nodes                                   |
| ----------- | --------------------------------------- |
| Events      | Start event, end event                  |
| Tasks       | Service task, script task, receive task |
| Gateways    | Exclusive, parallel, inclusive          |
| Composition | Call activity, embedded subprocess      |

The designer supports the elements listed above. Unsupported BPMN XML is reported during import or validation. Human
tasks are not supported because the CompileFlow runtime does not provide durable human-task execution.

An embedded subprocess owns a nested graph with its own start and end event. Workbench preserves that hierarchy in
visual and XML views. Sequence flows cannot cross the container boundary, event subprocesses are unsupported, and
receive tasks cannot be placed inside an embedded subprocess.

Service tasks use CompileFlow `cf:action` extensions. Script tasks default to the built-in `qlexpress` executor and may
name another executor installed in the engine. Process variables, action and
call-activity variable mappings, message definitions, plain-text documentation, and diagram geometry are retained across
XML and visual editing.

## Editing

- Drag nodes from the palette onto the canvas.
- Connect compatible ports to create sequence flows.
- Select a node or edge to edit its properties.
- Configure conditions on outgoing gateway flows and identify the default path where needed.
- Place subprocess children inside their container and keep all internal sequence flows within that boundary.
- Use the visual, XML, or split view for the same underlying process.
- Resolve validation findings before saving or exporting.

XML edits are parsed back into the structured model. If parsing fails, correct the reported error before switching back
to visual editing.

## Save, Snapshots, And Export

Saving a local workspace draft writes it to IndexedDB. Workbench stores local snapshots for that flow. Restoring a
snapshot loads its XML back into the editor; save the flow to persist the restored draft. Saving a draft opened from
Operate updates the Server draft with its expected revision, without creating local snapshots or publishing a Version.

Workspace export produces a JSON backup containing local flows, snapshots, and templates. BPMN XML export
produces an engine-facing process definition. These formats serve different purposes and are not interchangeable.

Browser storage is origin-specific and can be cleared by browser policy or user action. Use workspace export when the
local draft must be retained or moved to another device.

## Preview And Published Execution

Preview and published execution use separate paths:

- Learn/designer trial execution uses the loopback development mock in mock mode and Workbench Server preview in real
  mode.
- Browser simulation stops at an embedded subprocess; use Workbench Server preview to test compiled subprocess
  execution.
- Server validation, publication, routing, and execution belong to `compileflow-workbench-server` and the Java engine.
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
