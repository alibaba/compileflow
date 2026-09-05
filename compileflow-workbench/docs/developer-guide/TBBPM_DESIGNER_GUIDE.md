# TBBPM Designer Developer Guide

The current TBBPM designer implementation in CompileFlow Workbench follows the boundaries below. This is a developer
reference, not a release report or roadmap.

## Scope

The TBBPM designer is the Workbench editor for CompileFlow's TBBPM XML format. It provides:

- Drag-and-drop node creation on an X6 canvas.
- Node and edge property editing.
- XML import and export.
- Local workspace persistence through IndexedDB.
- Undo, redo, copy, paste, search, validation, and canvas zoom controls.

The designer is part of the Build module and does not own an Engine. Draft preview uses the loopback development mock or
`compileflow-workbench-server`; published execution remains an Operate concern.

## Code Entry Points

| Area                  | Path                                                                  |
| --------------------- | --------------------------------------------------------------------- |
| Designer shell        | `apps/web/src/authoring/designer/components/TbbpmDesigner.tsx`        |
| Canvas adapter        | `apps/web/src/authoring/designer/components/TbbpmCanvas.tsx`          |
| Palette               | `apps/web/src/authoring/designer/components/NodePalette.tsx`          |
| Property panel        | `apps/web/src/authoring/designer/components/TbbpmPropertiesPanel.tsx` |
| Node types            | `apps/web/src/authoring/designer/types/tbbpm.ts`                      |
| XML codec             | `apps/web/src/authoring/designer/serialization/tbbpmXmlCodec.ts`      |
| Validation entry      | `apps/web/src/authoring/designer/validation/TbbpmValidator.ts`        |
| Validation strategies | `apps/web/src/authoring/designer/validation/strategies/`              |
| Shared designer state | `apps/web/src/authoring/designer/store/`                              |

## Supported Node Types

The supported TBBPM node types are defined by `TbbpmNodeType`:

| Category     | Types                                                              |
| ------------ | ------------------------------------------------------------------ |
| Flow control | `start`, `end`                                                     |
| Tasks        | `autoTask`, `waitTask`, `waitEventTask`, `timerTask`, `scriptTask` |
| Gateways     | `exclusive`, `parallel`, `inclusive`                               |
| Subprocess   | `subBpm`, `bpmCall`                                                |
| Loop         | `while`, `foreach`                                                 |
| Loop control | `break`, `continue`                                                |
| Annotation   | `note`                                                             |

When adding a node type, update the type definition, palette registration, X6 node registration, property configuration,
XML parser/writer, validation strategy, i18n labels, and focused tests together.

## State And Persistence

Workbench uses Redux Toolkit for shared designer state. The editor state lives under
`apps/web/src/authoring/designer/store/`, while flow persistence uses IndexedDB through the storage APIs in
`apps/web/src/authoring/designer/api/`.

The TBBPM designer should keep the in-memory graph model and generated XML synchronized through structured model
updates. Avoid ad hoc XML string edits in UI components.

## Validation

Validation is split by responsibility:

- `TbbpmValidator` checks TBBPM-specific structure, node rules, and connection rules.
- `ProcessTopologyValidator` handles shared graph topology checks.
- Node-level rules are implemented under
  `apps/web/src/authoring/designer/validation/strategies/`.
- User-facing validation text should come from i18n keys, not hard-coded component strings.

Validation issues use `code`, `severity`, `category`, and optional `params`. New rules should include tests that assert
codes and severities rather than translated text.

## Expression Safety

Designer simulation evaluates edge, breakpoint, and simple script-assignment expressions with
`apps/web/src/authoring/designer/simulation/safeExpressionEvaluator.ts`. This evaluator is a small allowlisted
expression interpreter; it does not execute user-provided JavaScript. Keep the supported grammar narrow:
comparisons, boolean operators, arithmetic, parentheses, string literals, `null`/boolean literals, simple
variable/member lookup, and the helper functions exposed by the expression builder.

Do not reintroduce `eval`, `new Function`, dynamic import, or DOM/global-object access for client-side simulation. If
the expression builder gains new operators or functions, extend the interpreter and its unit tests in the same change.

## Keyboard Shortcuts

The shortcut list shown in the UI is generated from
`apps/web/src/authoring/designer/components/keyboardShortcutsData.ts`. Keep this guide aligned with that source.

| Shortcut                                 | Action                   |
| ---------------------------------------- | ------------------------ |
| `Ctrl/Cmd + S`                           | Save                     |
| `Ctrl/Cmd + Z`                           | Undo                     |
| `Ctrl/Cmd + Y` or `Ctrl/Cmd + Shift + Z` | Redo                     |
| `Ctrl/Cmd + C`                           | Copy selected nodes      |
| `Ctrl/Cmd + V`                           | Paste nodes              |
| `Delete`                                 | Delete selected elements |
| `Ctrl/Cmd + Plus` or `Ctrl/Cmd + =`      | Zoom in                  |
| `Ctrl/Cmd + Minus`                       | Zoom out                 |
| `Ctrl/Cmd + 0`                           | Fit canvas               |
| Double-click node                        | Open node properties     |
| Drag port                                | Create connection        |
| Drag node                                | Move node                |
| `Ctrl/Cmd + F`                           | Search                   |
| `Ctrl/Cmd + /`                           | Open shortcut help       |

## Development Commands

Run commands from `compileflow-workbench/`.

```bash
pnpm install
pnpm --filter @compileflow/workbench-web dev
pnpm type-check
pnpm --filter @compileflow/workbench-web test
pnpm --filter @compileflow/workbench-web test:e2e:smoke
```

For release-like local verification, use:

```bash
pnpm verify:delivery
```

Production deployment is documented in [`../../DEPLOYMENT.md`](../../DEPLOYMENT.md).

## Contribution Checklist

- Keep `TbbpmNodeType`, palette entries, X6 registration, property tabs, XML mapping, validation, and tests consistent.
- Do not introduce npm or yarn lockfiles; this is a pnpm workspace.
- Do not document performance numbers unless they come from a reproducible command and committed report.
- Do not add roadmap promises to this guide. Track planned work in issues instead.
- Keep examples and screenshots aligned with the current `/build` workflow.
